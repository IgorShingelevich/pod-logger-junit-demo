package com.example.demotest;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.k3s.K3sContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.LocalPortForward;
import io.fabric8.openshift.client.OpenShiftClient;

public final class ClusterLifecycle {

    private static final Logger log = LoggerFactory.getLogger(ClusterLifecycle.class);
    private static final String IMAGE = "demo-api:local";
    private static final String K3S_IMAGE = "rancher/k3s:v1.31.5-k3s1";

    private static K3sContainer k3s;
    private static OpenShiftClient client;
    private static LocalPortForward forward;
    private static int localPort;

    private ClusterLifecycle() {
    }

    public static synchronized void start() {
        if (k3s != null) {
            log.debug("ClusterLifecycle.start skipped: cluster already initialized on port {}", localPort);
            return;
        }
        String step = "build-image";
        try {
            log.debug("ClusterLifecycle.start step={} image={} k3sImage={}", step, IMAGE, K3S_IMAGE);
            buildImage();

            step = "start-k3s";
            log.debug("ClusterLifecycle.start step={}", step);
            k3s = new K3sContainer(DockerImageName.parse(K3S_IMAGE))
                    .withCommand("server", "--disable=traefik", "--tls-san=127.0.0.1")
                    .withLogConsumer(new Slf4jLogConsumer(LoggerFactory.getLogger("k3s")));
            k3s.start();

            step = "build-client";
            log.debug("ClusterLifecycle.start step={}", step);
            Config config = Config.fromKubeconfig(k3s.getKubeConfigYaml());
            config.setTrustCerts(true);
            client = new KubernetesClientBuilder().withConfig(config).build().adapt(OpenShiftClient.class);

            step = "import-image";
            log.debug("ClusterLifecycle.start step={}", step);
            importImage();

            step = "apply-manifest";
            log.debug("ClusterLifecycle.start step={}", step);
            applyManifest();

            step = "wait-for-pod";
            log.debug("ClusterLifecycle.start step={}", step);
            waitForPod();

            step = "port-forward";
            log.debug("ClusterLifecycle.start step={}", step);
            forward = client.services().inNamespace("default").withName("demo-api").portForward(8080);
            localPort = forward.getLocalPort();
            log.debug("ClusterLifecycle.start port-forward established on {}", localPort);

            step = "wait-for-http";
            log.debug("ClusterLifecycle.start step={}", step);
            waitForHttp();

            step = "register-shutdown-hook";
            log.debug("ClusterLifecycle.start step={}", step);
            Runtime.getRuntime().addShutdownHook(new Thread(ClusterLifecycle::stop));
            log.info("demo-api is reachable at http://127.0.0.1:{}", localPort);
        } catch (RuntimeException e) {
            log.error("ClusterLifecycle.start failed at step {}: {}", step, e.getMessage(), e);
            throw e;
        }
    }

    public static OpenShiftClient client() {
        return client;
    }

    public static int localPort() {
        return localPort;
    }

    static void stop() {
        log.debug("ClusterLifecycle.stop invoked");
        try {
            if (forward != null) {
                log.debug("Closing port-forward on {}", localPort);
                forward.close();
            }
        } catch (Exception e) {
            log.warn("port-forward close: {}", e.getMessage());
        }
        try {
            if (client != null) {
                log.debug("Closing OpenShift client");
                client.close();
            }
        } catch (Exception e) {
            log.warn("client close: {}", e.getMessage());
        }
        if (k3s != null) {
            log.debug("Stopping k3s container");
            k3s.stop();
        }
    }

    private static void buildImage() {
        Path demoApp = findDemoApp();
        Path jar = demoApp.resolve("target").resolve("demo-app.jar");
        log.debug("buildImage: demoApp={} jar={}", demoApp, jar);
        if (!Files.exists(jar)) {
            throw new IllegalStateException(
                    "demo-app jar is missing at " + jar + ". Run: mvn -pl demo-app -am package -DskipTests");
        }
        log.debug("buildImage: jar exists, starting docker build for {}", IMAGE);
        run(demoApp, "docker", "build", "-t", IMAGE, ".");
    }

    private static void importImage() {
        try {
            Path tar = Files.createTempFile("demo-api", ".tar");
            log.debug("importImage: created temporary image archive {}", tar.toAbsolutePath());
            run(Path.of("."), "docker", "save", "-o", tar.toAbsolutePath().toString(), IMAGE);
            k3s.copyFileToContainer(MountableFile.forHostPath(tar), "/tmp/demo-api.tar");
            exec("ctr", "-n", "k8s.io", "images", "import", "/tmp/demo-api.tar");
            try {
                exec("ctr", "-n", "k8s.io", "images", "tag", "docker.io/library/demo-api:local", IMAGE);
            } catch (Exception tagError) {
                log.warn("Image tag skipped (import may already use {}): {}", IMAGE, tagError.getMessage());
            }
            Files.deleteIfExists(tar);
            log.debug("importImage: removed temporary image archive {}", tar.toAbsolutePath());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to import demo-api image into k3s", e);
        }
    }

    private static void applyManifest() {
        log.debug("applyManifest: loading /k8s/demo-api.yaml from test resources");
        try (InputStream in = ClusterLifecycle.class.getResourceAsStream("/k8s/demo-api.yaml")) {
            if (in == null) {
                throw new IllegalStateException("Missing classpath resource /k8s/demo-api.yaml");
            }
            client.load(in).inNamespace("default").createOrReplace();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to apply k8s manifest", e);
        }
    }

    private static void waitForPod() {
        log.debug("waitForPod: awaiting ready pod with label app=demo-api");
        org.awaitility.Awaitility.await()
                .atMost(2, TimeUnit.MINUTES)
                .pollInterval(2, TimeUnit.SECONDS)
                .until(() -> {
                    List<Pod> pods = client.pods().inNamespace("default").withLabel("app", "demo-api").list().getItems();
                    return pods.stream().anyMatch(ClusterLifecycle::isReady);
                });
    }

    private static boolean isReady(Pod pod) {
        return pod.getStatus() != null
                && "Running".equals(pod.getStatus().getPhase())
                && pod.getStatus().getContainerStatuses() != null
                && pod.getStatus().getContainerStatuses().stream().allMatch(cs -> Boolean.TRUE.equals(cs.getReady()));
    }

    private static void waitForHttp() {
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        log.debug("waitForHttp: awaiting GET /health on forwarded port {}", localPort);
        org.awaitility.Awaitility.await()
                .atMost(1, TimeUnit.MINUTES)
                .pollInterval(1, TimeUnit.SECONDS)
                .until(() -> {
                    try {
                        HttpRequest req = HttpRequest.newBuilder(
                                URI.create("http://127.0.0.1:" + localPort + "/health")).GET().build();
                        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
                        return res.statusCode() == 200 && res.body().contains("UP");
                    } catch (Exception e) {
                        return false;
                    }
                });
    }

    private static void exec(String... cmd) throws Exception {
        log.debug("exec in k3s: {}", String.join(" ", cmd));
        ExecResult result = k3s.execInContainer(cmd);
        if (result.getExitCode() != 0) {
            String joined = String.join(" ", cmd);
            if (joined.contains("images tag") && result.getStderr().contains("already exists")) {
                return;
            }
            throw new IllegalStateException("k3s exec failed (" + joined + "): " + result.getStderr());
        }
        log.debug("exec completed: {} exitCode={}", String.join(" ", cmd), result.getExitCode());
    }

    private static void run(Path workDir, String... command) {
        try {
            log.debug("run command: cwd={} command={}", workDir.toAbsolutePath(), Arrays.stream(command).collect(Collectors.joining(" ")));
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(workDir.toFile());
            pb.redirectErrorStream(true);
            Process process = pb.start();
            String output = new String(process.getInputStream().readAllBytes());
            int code = process.waitFor();
            if (code != 0) {
                throw new IllegalStateException(Arrays.toString(command) + " failed:\n" + output);
            }
            log.info("{} -> {}", Arrays.stream(command).collect(Collectors.joining(" ")), output);
        } catch (Exception e) {
            throw new IllegalStateException("Command failed: " + Arrays.toString(command), e);
        }
    }

    private static Path findDemoApp() {
        Path userDir = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        List<Path> candidates = List.of(
                userDir.resolve("demo-app"),
                userDir.resolve("..").resolve("demo-app").normalize(),
                userDir.getParent() != null ? userDir.getParent().resolve("demo-app") : userDir);
        Path resolved = candidates.stream()
                .filter(p -> Files.isDirectory(p) && Files.exists(p.resolve("Dockerfile")))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Cannot find demo-app directory from " + userDir));
        log.debug("findDemoApp: resolved {} from {}", resolved, userDir);
        return resolved;
    }
}
