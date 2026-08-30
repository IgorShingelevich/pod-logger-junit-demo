package com.example.podlogger.client;

import java.time.LocalDateTime;
import java.util.UUID;

import com.example.podlogger.store.EnvironmentType;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PodLogDto {

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
    private LocalDateTime timestamp;

    private String level;
    private String message;
    private String logger;
    private String stackTrace;
    private String threadName;
    private String traceId;
    private String spanId;

    private UUID id;
    private UUID testRunId;
    private String runName;
    private String testRunName;
    private String testSuiteName;
    private String relatedTestClass;
    private String relatedTestMethod;
    private String testDisplayName;
    private EnvironmentType environmentType;
    private String serviceType;
    private Boolean testFailed;

    private String podName;
    private String namespace;
    private String containerName;
    private String podLabelSelector;
    private String nodeName;

    private String fingerprint;
    private String errorCategory;
}
