package com.example.podlogger;

import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Component
public class PodLoggerProperties {

    private boolean collectOnFailOnly = true;
    private String namespace = "default";
    private String podLabelSelector = "app=demo-api";
}
