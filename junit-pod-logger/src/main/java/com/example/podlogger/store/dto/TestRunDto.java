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
public class TestRunDto {

    private UUID id;
    private String testRunName;
    private String testSuiteName;
    private EnvironmentType environmentType;
    private String serviceType;
    private String namespace;
    private String podLabelSelector;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private String status;
}
