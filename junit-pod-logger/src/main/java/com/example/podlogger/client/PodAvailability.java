package com.example.podlogger.client;

import java.util.List;

import lombok.Builder;
import lombok.Value;

/**
 * Результат {@code isPodAvailable()} / {@link com.example.podlogger.client.OpenshiftClient#probePodAvailability()}.
 *
 * <p>{@link #available} = нет stand-down Event <em>и</em> health зелёный (Ready [+ HTTP]).
 * Fail-fast прогона триггерит только {@link #standDownEventPresent}, не красный health.
 */
@Value
@Builder
public class PodAvailability {

    /** Итог: можно ли persist'ить логи и считать под живой. */
    boolean available;
    /** Есть ли Event из allowlist stand-down. Единственный триггер fail-fast. */
    boolean standDownEventPresent;
    /** Kubernetes Ready и (если задан URL) HTTP 2xx без maintenance-текста. */
    boolean healthPassed;
    /** Код первого stand-down Event либо {@code PodNotReady} / {@code HealthCheckFailed}. */
    String code;
    /** Пояснение для лога и exception. */
    String message;

    /** Найденные stand-down Events; пустой список если их нет. */
    @Builder.Default
    List<PodEventDto> standDownEvents = List.of();

    /**
     * Под доступна: нет stand-down, health зелёный.
     *
     * @return immutable instance
     */
    public static PodAvailability up() {
        return builder()
                .available(true)
                .standDownEventPresent(false)
                .healthPassed(true)
                .standDownEvents(List.of())
                .build();
    }

    /**
     * Stand-down: {@code available=false}, {@code standDownEventPresent=true}.
     *
     * @param code    код первого Event
     * @param message текст
     * @param events  список stand-down (копируется)
     * @return immutable instance
     */
    public static PodAvailability standDown(String code, String message, List<PodEventDto> events) {
        List<PodEventDto> copy = events == null ? List.of() : List.copyOf(events);
        return builder()
                .available(false)
                .standDownEventPresent(true)
                .healthPassed(false)
                .code(code)
                .message(message)
                .standDownEvents(copy)
                .build();
    }

    /**
     * Красный health без stand-down Event: persist skip, прогон продолжается.
     *
     * @param code    {@code PodNotReady} или {@code HealthCheckFailed}
     * @param message подробности
     * @return immutable instance
     */
    public static PodAvailability healthFailed(String code, String message) {
        return builder()
                .available(false)
                .standDownEventPresent(false)
                .healthPassed(false)
                .code(code)
                .message(message)
                .standDownEvents(List.of())
                .build();
    }
}
