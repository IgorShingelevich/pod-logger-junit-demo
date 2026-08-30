package com.example.podlogger.client;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Kubernetes core/v1 Event, спроецированный в DTO библиотеки.
 *
 * <p>В v1 {@link #code} и {@link #reason} равны: отдельного поля «код» в k8s Event нет,
 * код = {@code Event.reason}. Allure и тесты проверяют {@code code}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PodEventDto {

    /**
     * Машиночитаемый код. Равен Kubernetes {@code Event.reason}
     * ({@code TestRunStarted}, {@code Maintenance}, {@code Evicted}, …).
     */
    private String code;

    /** То же значение, что {@link #code}, для совместимости с k8s-именем поля. */
    private String reason;
    /** {@code Normal} или {@code Warning}. */
    private String type;
    /** Текст Event без секретов. */
    private String message;

    /** {@code lastTimestamp} иначе {@code eventTime} иначе {@code creationTimestamp}, UTC. */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
    private LocalDateTime timestamp;

    /** Счётчик повторов k8s Event. */
    private Integer count;
    /** Имя вовлечённой поды. */
    private String podName;
    /** Namespace Event. */
    private String namespace;
    /** UID metadata Event. */
    private String uid;
}
