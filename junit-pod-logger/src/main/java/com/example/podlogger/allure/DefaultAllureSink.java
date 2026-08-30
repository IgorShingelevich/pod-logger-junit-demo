package com.example.podlogger.allure;

import io.qameta.allure.Allure;

public class DefaultAllureSink implements AllureSink {

    @Override
    public void addAttachment(String name, String contentType, String body, String fileExtension) {
        Allure.addAttachment(name, contentType, body, fileExtension);
    }
}
