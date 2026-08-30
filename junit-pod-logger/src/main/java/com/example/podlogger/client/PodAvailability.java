package com.example.podlogger.client;

import java.util.List;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class PodAvailability {

    boolean available;
    boolean standDownEventPresent;
    boolean healthPassed;
    String code;
    String message;

    @Builder.Default
    List<PodEventDto> standDownEvents = List.of();

    public static PodAvailability up() {
        return builder()
                .available(true)
                .standDownEventPresent(false)
                .healthPassed(true)
                .standDownEvents(List.of())
                .build();
    }

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
