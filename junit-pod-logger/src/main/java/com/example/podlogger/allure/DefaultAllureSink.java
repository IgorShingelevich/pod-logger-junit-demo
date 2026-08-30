package com.example.podlogger.allure;

import io.qameta.allure.Allure;

/**
 * Прод-sink: делегирует в {@link Allure#addAttachment(String, String, String, String)}.
 */
public class DefaultAllureSink implements AllureSink {

    /**
     * {@inheritDoc}
     */
    @Override
    public void addAttachment(String name, String contentType, String body, String fileExtension) {
        Allure.addAttachment(name, contentType, body, fileExtension);
    }
}
