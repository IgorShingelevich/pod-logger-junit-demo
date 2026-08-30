package com.example.podlogger.store.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import com.example.podlogger.store.EnvironmentType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogQuery {

    private UUID testRunId;
    private String runName;
    private String testRunName;
    private String testSuiteName;
    private String relatedTestClass;
    private String relatedTestMethod;
    private EnvironmentType environmentType;
    private String serviceType;
    private LocalDateTime from;
    private LocalDateTime to;
    private String level;
    private String logger;
    private String messageContains;
    private String testDisplayName;
    private Boolean testFailed;
    private String fingerprint;
    private Integer limit;

    public String effectiveRunName() {
        if (runName != null && !runName.isBlank()) {
            return runName;
        }
        return testRunName;
    }
}
