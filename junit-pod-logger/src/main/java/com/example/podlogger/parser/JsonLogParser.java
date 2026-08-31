package com.example.podlogger.parser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.example.podlogger.client.PodLogDto;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

/**
 * Построчный JSON-парсер dump'а {@code pods/log}.
 *
 * <p>Каждая строка, начинающаяся с {@code \{} после trim, рассматривается как новый
 * JSON-кандидат для {@link PodLogDto}. Если десериализация успешна, последующие
 * non-JSON строки приклеиваются к {@link PodLogDto#getStackTrace()} этого DTO
 * до следующего JSON-кандидата. Это позволяет не потерять column-style stack trace,
 * который приложение пишет отдельными строками после основной JSON-записи.
 *
 * <p>Поля контекста прогона из stdout не приходят и здесь не заполняются.
 * Невалидный JSON логируется на debug вместе с throwable и пропускается.
 */
@Component
@RequiredArgsConstructor
public class JsonLogParser implements LogParser {

    private static final Logger log = LoggerFactory.getLogger(JsonLogParser.class);
    private static final int MAX_DEBUG_SNIPPET = 200;

    private final ObjectMapper objectMapper;

    /**
     * {@inheritDoc}
     *
     * <p>Этапы:
     * <ol>
     *   <li>читать dump построчно без {@code split("\\R")}, чтобы не плодить лишний массив
     *       для большого ответа;</li>
     *   <li>распознавать старт нового JSON-объекта по первой значащей {@code \{};</li>
     *   <li>успешный JSON превращать в {@link PodLogDto};</li>
     *   <li>строки-продолжения без {@code \{} приклеивать к {@code stackTrace}
     *       последнего успешно распознанного DTO;</li>
     *   <li>на debug писать классификацию каждой строки и итоговые счётчики.</li>
     * </ol>
     */
    @Override
    public List<PodLogDto> parse(String rawDump) {
        List<PodLogDto> logs = new ArrayList<>();
        if (rawDump == null) {
            log.debug("Skip pod log parse: raw dump is null");
            return logs;
        }
        if (rawDump.isBlank()) {
            log.debug("Skip pod log parse: raw dump is blank");
            return logs;
        }

        int lineNumber = 0;
        int continuationCount = 0;
        int preambleCount = 0;
        int jacksonFailures = 0;
        PodLogDto current = null;

        log.debug("Starting pod log parse: chars={}", rawDump.length());
        try (BufferedReader reader = new BufferedReader(new StringReader(rawDump))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                String trimmed = line.trim();
                if (trimmed.startsWith("{")) {
                    current = null;
                    try {
                        PodLogDto parsed = objectMapper.readValue(trimmed, PodLogDto.class);
                        logs.add(parsed);
                        current = parsed;
                        log.debug("Line {} parsed into DTO: {}", lineNumber, describeDto(parsed));
                    } catch (Exception e) {
                        jacksonFailures++;
                        log.debug(
                                "Line {} is not valid PodLogDto JSON: {}",
                                lineNumber,
                                abbreviate(trimmed),
                                e);
                    }
                    continue;
                }

                if (current != null) {
                    appendContinuation(current, line);
                    continuationCount++;
                    log.debug(
                            "Line {} appended to stackTrace of current DTO: {}",
                            lineNumber,
                            abbreviate(line));
                    continue;
                }

                preambleCount++;
                log.debug("Line {} skipped before first DTO: {}", lineNumber, abbreviate(line));
            }
        } catch (IOException e) {
            throw new IllegalStateException("Unexpected IO error while parsing in-memory pod log dump", e);
        }

        log.debug(
                "Finished pod log parse: lines={}, dtoCount={}, continuationLines={}, preambleLines={}, jacksonFailures={}",
                lineNumber,
                logs.size(),
                continuationCount,
                preambleCount,
                jacksonFailures);
        return logs;
    }

    private static void appendContinuation(PodLogDto current, String line) {
        String existing = current.getStackTrace();
        if (existing == null || existing.isEmpty()) {
            current.setStackTrace(line);
            return;
        }
        current.setStackTrace(existing + "\n" + line);
    }

    private static String describeDto(PodLogDto dto) {
        return "timestamp=" + dto.getTimestamp()
                + ", level=" + dto.getLevel()
                + ", logger=" + dto.getLogger()
                + ", message=" + abbreviate(dto.getMessage())
                + ", stackTracePresent=" + (dto.getStackTrace() != null && !dto.getStackTrace().isBlank());
    }

    private static String abbreviate(String value) {
        if (value == null) {
            return "null";
        }
        String singleLine = value.replace("\r", "\\r").replace("\n", "\\n");
        if (singleLine.length() <= MAX_DEBUG_SNIPPET) {
            return singleLine;
        }
        return singleLine.substring(0, MAX_DEBUG_SNIPPET) + "...";
    }
}
