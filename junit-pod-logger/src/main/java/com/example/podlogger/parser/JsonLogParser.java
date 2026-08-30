package com.example.podlogger.parser;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.example.podlogger.client.PodLogDto;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class JsonLogParser implements LogParser {

    private static final Logger log = LoggerFactory.getLogger(JsonLogParser.class);

    private final ObjectMapper objectMapper;

    @Override
    public List<PodLogDto> parse(String rawDump) {
        List<PodLogDto> events = new ArrayList<>();
        if (rawDump == null || rawDump.isBlank()) {
            return events;
        }
        for (String line : rawDump.split("\\R")) {
            String trimmed = line.trim();
            if (!trimmed.startsWith("{")) {
                continue;
            }
            try {
                events.add(objectMapper.readValue(trimmed, PodLogDto.class));
            } catch (Exception e) {
                log.debug("Skipping non-DTO log line: {}", trimmed);
            }
        }
        return events;
    }
}
