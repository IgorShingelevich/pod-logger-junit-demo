package com.example.podlogger.client;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.example.podlogger.store.EnvironmentType;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Одна JSON-строка лога поды плюс контекст тестового прогона.
 *
 * <p>Из stdout поды парсер заполняет в первую очередь {@code timestamp}, {@code level},
 * {@code message}, {@code logger} (и опционально stack/trace). Поля прогона
 * ({@code testRunId}, suite, environment, related test, fingerprint) проставляет
 * {@code PodLoggerService} / store после parse.
 *
 * <p>{@link #relevantEvents} — runtime/Allure only: на упавшем invocation копируется
 * один список Events на каждую запись окна. В SQLite колонки нет, {@code FingerprintUtil}
 * поле игнорирует. С поды поле не приходит ({@link JsonIgnoreProperties}).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PodLogDto {

    /** Время события в логе поды (UTC, миллисекунды). */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
    private LocalDateTime timestamp;

    /** Уровень (ERROR, INFO, …). */
    private String level;
    /** Текст сообщения. */
    private String message;
    /** Logger name из JSON приложения. */
    private String logger;
    /** Стек, если приложение его кладёт в JSON. */
    private String stackTrace;
    /** Имя потока. */
    private String threadName;
    /** Трейс, если есть. */
    private String traceId;
    /** Спан, если есть. */
    private String spanId;

    /** PK строки {@code log_entry}; генерируется при save. */
    private UUID id;
    /** FK на {@code test_run}; обязателен для persist. */
    private UUID testRunId;
    /** Имя прогона (дубль {@link #testRunName} для совместимости фильтров). */
    private String runName;
    /** Человекочитаемое имя прогона; может повторяться между запусками. */
    private String testRunName;
    /** Имя suite. */
    private String testSuiteName;
    /** FQCN тестового класса invocation. */
    private String relatedTestClass;
    /** Имя тестового метода. */
    private String relatedTestMethod;
    /** JUnit display name (для parameterized — код кейса). */
    private String testDisplayName;
    /** Стенд прогона. */
    private EnvironmentType environmentType;
    /** Какой сервис/под. */
    private String serviceType;
    /** Упал ли invocation, породивший эту запись. */
    private Boolean testFailed;

    /** Имя поды. */
    private String podName;
    /** Namespace поды. */
    private String namespace;
    /** Контейнер, если известен. */
    private String containerName;
    /** Селектор, которым искали под. */
    private String podLabelSelector;
    /** Node, если известен. */
    private String nodeName;

    /** SHA-256 от level+logger+message+stack; без {@code relevantEvents}. */
    private String fingerprint;
    /** Резерв под analysis-слой; в v1 не заполняется. */
    private String errorCategory;

    /**
     * Pod Events окна упавшего invocation. Не персистится.
     */
    private List<PodEventDto> relevantEvents;
}
