package com.example.demotest;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.fabric8.openshift.client.OpenShiftClient;

@Configuration
public class ClusterConfig {

    @Bean(destroyMethod = "")
    public OpenShiftClient fabric8OpenShiftClient() {
        ClusterLifecycle.start();
        return ClusterLifecycle.client();
    }

    @Bean
    public Integer demoApiPort() {
        ClusterLifecycle.start();
        return ClusterLifecycle.localPort();
    }
}
