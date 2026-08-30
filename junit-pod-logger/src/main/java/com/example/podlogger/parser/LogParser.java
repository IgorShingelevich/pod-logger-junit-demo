package com.example.podlogger.parser;

import java.util.List;

import com.example.podlogger.client.PodLogDto;

/**
 * Контракт парсера stdout поды: одна строка dump → ноль или одна {@link PodLogDto}.
 */
public interface LogParser {

    /**
     * Разбирает полный dump. Строки не-JSON и шум kube пропускаются, а не валят вызов.
     *
     * @param rawDump сырой ответ {@code pods/log}; {@code null}/blank → пустой список
     * @return упорядоченный список распознанных JSON-событий
     */
    List<PodLogDto> parse(String rawDump);
}
