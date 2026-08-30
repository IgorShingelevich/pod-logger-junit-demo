package com.example.demotest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@DisplayName("Infrastructure Logging Test")
@TestMethodOrder(OrderAnnotation.class)
class InfrastructureLoggingTest {

    private static final Logger log = LoggerFactory.getLogger(InfrastructureLoggingTest.class);
    private static final String IMAGE = "demo-api:local";

    @Test
    @Order(1)
    @DisplayName("step 1 - docker is reachable for local infrastructure tests")
    void dockerIsReachable() {
        log.info("Step 1/3: checking Docker availability before any image build or deployment");
        log.info("Expected setup: Docker Desktop is running and DOCKER_HOST points to tcp://127.0.0.1:2375 when needed");

        CommandResult result = runCommand(findRepoRoot(), "docker", "info");

        assertEquals(0, result.exitCode(),
                "Docker is not reachable. Start Docker Desktop, enable tcp://localhost:2375 without TLS, "
                        + "and rerun the test.\n" + result.output());
        assertFalse(result.output().isBlank(), "docker info returned no output");

        log.info("Docker is available. First output lines:\n{}", firstLines(result.output(), 12));
    }

    @Test
    @Order(2)
    @DisplayName("step 2 - demo api image can be built from the packaged application")
    void demoApiImageCanBeBuilt() {
        Path demoAppDir = findDemoApp();
        Path jar = demoAppDir.resolve("target").resolve("demo-app.jar");

        log.info("Step 2/3: validating the application artifact required for docker build");
        log.info("The image build depends on {}", jar);

        assertTrue(Files.exists(jar),
                "Missing " + jar + ". Build the application first with: mvn -pl demo-app -am package -DskipTests");

        log.info("Packaging prerequisite is present. Building image {} from {}", IMAGE, demoAppDir);
        CommandResult build = runCommand(demoAppDir, "docker", "build", "-t", IMAGE, ".");

        assertEquals(0, build.exitCode(), "docker build failed:\n" + build.output());
        assertTrue(build.output().contains("Successfully") || build.output().contains("naming to docker.io/library/" + IMAGE)
                || build.output().contains("writing image"),
                "docker build finished without an expected success marker:\n" + build.output());

        log.info("Image build completed. Tail of docker build output:\n{}", lastLines(build.output(), 20));
    }

    @Test
    @Order(3)
    @DisplayName("step 3 - local cluster deployment becomes reachable")
    void deploymentBecomesReachable() throws Exception {
        log.info("Step 3/3: starting local k3s, importing {}, applying manifests, and waiting for readiness", IMAGE);

        ClusterLifecycle.start();

        assertNotNull(ClusterLifecycle.client(), "Kubernetes client was not initialized");
        assertTrue(ClusterLifecycle.localPort() > 0, "Port-forward was not established");

        String body = readHealthEndpoint(ClusterLifecycle.localPort());

        assertTrue(body.contains("UP"), "Health endpoint did not report UP. Body:\n" + body);

        log.info("Deployment is reachable on forwarded port {}", ClusterLifecycle.localPort());
        log.info("Infrastructure is ready. Next expected demo step: run OrderErrorIT to get one failed Allure result "
                + "per error case with its own pod log attachment.");
    }

    private static String readHealthEndpoint(int port) throws Exception {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/health"))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), "Unexpected /health status");
        return response.body();
    }

    private static Path findRepoRoot() {
        Path userDir = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        List<Path> candidates = List.of(
                userDir,
                userDir.getParent() != null ? userDir.getParent() : userDir);
        return candidates.stream()
                .filter(path -> Files.exists(path.resolve("pom.xml")) && Files.isDirectory(path.resolve("demo-tests")))
                .findFirst()
                .orElse(userDir);
    }

    private static Path findDemoApp() {
        Path repoRoot = findRepoRoot();
        Path demoApp = repoRoot.resolve("demo-app");
        if (Files.isDirectory(demoApp) && Files.exists(demoApp.resolve("Dockerfile"))) {
            return demoApp;
        }
        throw new IllegalStateException("Cannot find demo-app directory from " + repoRoot);
    }

    private static CommandResult runCommand(Path workDir, String... command) {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.directory(workDir.toFile());
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();
            String output = new String(process.getInputStream().readAllBytes());
            int exitCode = process.waitFor();
            return new CommandResult(exitCode, output);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Failed to run command in " + workDir + ": " + String.join(" ", command), e);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to run command in " + workDir + ": " + String.join(" ", command), e);
        }
    }

    private static String firstLines(String text, int maxLines) {
        return text.lines().limit(maxLines).reduce((left, right) -> left + System.lineSeparator() + right).orElse("");
    }

    private static String lastLines(String text, int maxLines) {
        List<String> lines = text.lines().toList();
        int fromIndex = Math.max(0, lines.size() - maxLines);
        return String.join(System.lineSeparator(), lines.subList(fromIndex, lines.size()));
    }

    private record CommandResult(int exitCode, String output) {
    }
}
