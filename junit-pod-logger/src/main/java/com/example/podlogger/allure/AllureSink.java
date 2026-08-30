package com.example.podlogger.allure;

/**
 * Testable wrapper around {@code Allure.addAttachment}.
 */
public interface AllureSink {

    void addAttachment(String name, String contentType, String body, String fileExtension);
}
