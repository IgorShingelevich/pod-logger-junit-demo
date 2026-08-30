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
            return;
        }
        buildImage();
        k3s = new K3sContainer(DockerImageName.parse(K3S_IMAGE))
                .withCommand("server", "--disable=traefik", "--tls-san=127.0.0.1")
                .withLogConsumer(new Slf4jLogConsumer(LoggerFactory.getLogger("k3s")));
        k3s.start();

        Config config = Config.fromKubeconfig(k3s.getKubeConfigYaml());
        config.setTrustCerts(true);
        client = new KubernetesClientBuilder().withConfig(config).build().adapt(OpenShiftClient.class);

        importImage();
        applyManifest();
        waitForPod();
        forward = client.services().inNamespace("default").withName("demo-api").portForward(8080);
        localPort = forward.getLocalPort();
        waitForHttp();
        Runtime.getRuntime().addShutdownHook(new Thread(ClusterLifecycle::stop));
        log.info("demo-api is reachable at http://127.0.0.1:{}", localPort);
    }

    public static OpenShiftClient client() {
        return client;
    }

    public static int localPort() {
        return localPort;
    }

    static void stop() {
        try {
            if (forward != null) {
                forward.close();
            }
        } catch (Exception e) {
            log.warn("port-forward close: {}", e.getMessage());
        }
        try {
            if (client != null) {
                client.close();
            }
        } catch (Exception e) {
            log.warn("client close: {}", e.getMessage());
        }
        if (k3s != null) {
            k3s.stop();
        }
    }

    private static void buildImage() {
        Path demoApp = findDemoApp();
        Path jar = demoApp.resolve("target").resolve("demo-app.jar");
        if (!Files.exists(jar)) {
            throw new IllegalStateException(
                    "demo-app jar is missing at " + jar + ". Run: mvn -pl demo-app -am package -DskipTests");
        }
        run(demoApp, "docker", "build", "-t", IMAGE, ".");
    }

    private static void importImage() {
        try {
            Path tar = Files.createTempFile("demo-api", ".tar");
            run(Path.of("."), "docker", "save", "-o", tar.toAbsolutePath().toString(), IMAGE);
            k3s.copyFileToContainer(MountableFile.forHostPath(tar), "/tmp/demo-api.tar");
            exec("ctr", "-n", "k8s.io", "images", "import", "/tmp/demo-api.tar");
            try {
                exec("ctr", "-n", "k8s.io", "images", "tag", "docker.io/library/demo-api:local", IMAGE);
            } catch (Exception tagError) {
                log.warn("Image tag skipped (import may already use {}): {}", IMAGE, tagError.getMessage());
            }
            Files.deleteIfExists(tar);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to import demo-api image into k3s", e);
        }
    }

    private static void applyManifest() {
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
        ExecResult result = k3s.execInContainer(cmd);
        if (result.getExitCode() != 0) {
            String joined = String.join(" ", cmd);
            if (joined.contains("images tag") && result.getStderr().contains("already exists")) {
                return;
            }
            throw new IllegalStateException("k3s exec failed (" + joined + "): " + result.getStderr());
        }
    }

    private static void run(Path workDir, String... command) {
        try {
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
        return candidates.stream()
                .filter(p -> Files.isDirectory(p) && Files.exists(p.resolve("Dockerfile")))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Cannot find demo-app directory from " + userDir));
    }
}
