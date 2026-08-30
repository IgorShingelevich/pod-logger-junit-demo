package com.example.podlogger.client;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.example.podlogger.PodLoggerProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.openshift.client.OpenShiftClient;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class OpenshiftClient {

    private static final Logger log = LoggerFactory.getLogger(OpenshiftClient.class);

    private final OpenShiftClient fabric8;
    private final PodLoggerProperties properties;
    private final ObjectMapper objectMapper;

    /**
     * Fetches the full pod log dump as one string from the Kubernetes API, then
     * parses JSON lines into {@link PodLogDto}.
     */
    public List<PodLogDto> getLog() {
        String raw = fetchRawLog();
        return parseLogDump(raw);
    }

    String fetchRawLog() {
        String namespace = properties.getNamespace();
        String[] selector = splitSelector(properties.getPodLabelSelector());
        List<Pod> pods = fabric8.pods()
                .inNamespace(namespace)
                .withLabel(selector[0], selector[1])
                .list()
                .getItems();
        if (pods.isEmpty()) {
            throw new IllegalStateException(
                    "No pods found in namespace " + namespace + " with selector " + properties.getPodLabelSelector());
        }
        Pod ready = pods.stream()
                .filter(OpenshiftClient::isReady)
                .findFirst()
                .orElse(pods.get(0));
        String name = ready.getMetadata().getName();
        log.debug("Reading logs from pod {}/{}", namespace, name);
        return fabric8.pods().inNamespace(namespace).withName(name).getLog();
    }

    List<PodLogDto> parseLogDump(String raw) {
        List<PodLogDto> events = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return events;
        }
        for (String line : raw.split("\\R")) {
            String trimmed = line.trim();
            if (!trimmed.startsWith("{")) {
                continue;
            }
            try {
                events.add(objectMapper.readValue(trimmed, PodLogDto.class));
            } catch (Exception e) {
                log.debug("Skipping non-DTO log line: {}", trimmed);
            }
        }
        return events;
    }

    private static boolean isReady(Pod pod) {
        return pod.getStatus() != null
                && "Running".equals(pod.getStatus().getPhase())
                && pod.getStatus().getContainerStatuses() != null
                && pod.getStatus().getContainerStatuses().stream().allMatch(cs -> Boolean.TRUE.equals(cs.getReady()));
    }

    static String[] splitSelector(String selector) {
        int eq = selector.indexOf('=');
        if (eq <= 0) {
            throw new IllegalArgumentException("podLabelSelector must be key=value, got: " + selector);
        }
        return new String[] {selector.substring(0, eq), selector.substring(eq + 1)};
    }
}
