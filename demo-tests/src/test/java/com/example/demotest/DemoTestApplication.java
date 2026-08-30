package com.example.demotest;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication(scanBasePackages = {"com.example.demotest", "com.example.podlogger"})
@Import(ClusterConfig.class)
public class DemoTestApplication {
}
