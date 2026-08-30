package com.example.podlogger;

import org.springframework.stereotype.Component;

import com.example.podlogger.store.EnvironmentType;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Component
public class PodLoggerProperties {

    private boolean collectOnFailOnly = true;
    private String namespace = "default";
    private String podLabelSelector = "app=demo-api";
    private String storePath = "";
    private EnvironmentType environmentType = EnvironmentType.LOCAL;
    private String testRunName = "";
    private String testSuiteName = "";
    private String serviceType = "";
    private boolean attachRunSummaryToAllure = false;
    private int queryLimit = 10_000;
    private int retentionDays = 30;
}
