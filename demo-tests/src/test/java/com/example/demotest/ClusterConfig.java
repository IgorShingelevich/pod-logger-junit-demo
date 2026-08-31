package com.example.demotest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.fabric8.openshift.client.OpenShiftClient;

@Configuration
public class ClusterConfig {

    private static final Logger log = LoggerFactory.getLogger(ClusterConfig.class);

    @Bean(destroyMethod = "")
    public OpenShiftClient fabric8OpenShiftClient() {
        log.debug("Creating fabric8 OpenShiftClient bean via ClusterLifecycle.start()");
        ClusterLifecycle.start();
        log.debug("Returning shared OpenShiftClient instance from ClusterLifecycle");
        return ClusterLifecycle.client();
    }

    @Bean
    public Integer demoApiPort() {
        log.debug("Resolving demoApiPort bean via ClusterLifecycle.start()");
        ClusterLifecycle.start();
        log.debug("Returning demo API forwarded port {}", ClusterLifecycle.localPort());
        return ClusterLifecycle.localPort();
    }
}
