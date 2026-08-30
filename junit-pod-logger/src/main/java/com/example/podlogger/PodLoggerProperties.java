package com.example.podlogger;

import java.util.List;

import org.springframework.stereotype.Component;

import com.example.podlogger.event.PodEventReasons;
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

    private boolean publishLifecycleEvents = true;
    private boolean failFastOnStandDownEvent = true;
    private String healthCheckUrl = "";
    private List<String> standDownEventCodes = List.of();
    private List<String> standDownMessagePatterns = List.of();

    public List<String> effectiveStandDownEventCodes() {
        if (standDownEventCodes == null || standDownEventCodes.isEmpty()) {
            return PodEventReasons.DEFAULT_STAND_DOWN_CODES;
        }
        return standDownEventCodes;
    }

    public List<String> effectiveStandDownMessagePatterns() {
        if (standDownMessagePatterns == null || standDownMessagePatterns.isEmpty()) {
            return PodEventReasons.DEFAULT_MESSAGE_PATTERNS;
        }
        return standDownMessagePatterns;
    }
}
