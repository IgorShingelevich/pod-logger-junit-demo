package com.example.podlogger.allure;

/**
 * Тестируемая обёртка над {@code Allure.addAttachment}.
 * Прод-реализация — {@link DefaultAllureSink}; в EngineTestKit-тестах подменяется
 * записывающим listener'ом, чтобы не зависеть от static Allure.
 */
public interface AllureSink {

    /**
     * Кладёт текстовый аттач в текущий Allure-тест.
     *
     * @param name          имя аттача ({@code pod-logs-...}, {@code pod-events-...})
     * @param contentType   MIME, обычно {@code application/json}
     * @param body          тело
     * @param fileExtension суффикс файла, обычно {@code .json}
     */
    void addAttachment(String name, String contentType, String body, String fileExtension);
}
