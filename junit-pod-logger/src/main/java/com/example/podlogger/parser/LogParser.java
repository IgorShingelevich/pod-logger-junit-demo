package com.example.podlogger.parser;

import java.util.List;

import com.example.podlogger.client.PodLogDto;

/**
 * Контракт парсера stdout поды: сырой dump {@code pods/log} → список {@link PodLogDto}.
 *
 * <p>Парсер работает только со stdout/stderr контейнера. Kubernetes не навязывает
 * JSON-схему строк лога, поэтому конкретные поля DTO зависят от encoder'а приложения,
 * а не от API кластера.
 *
 * <p>Реализация может:
 * <ul>
 *   <li>пропускать kube-preamble и иные строки, которые не становятся DTO;</li>
 *   <li>делегировать JSON-десериализацию {@code ObjectMapper};</li>
 *   <li>приклеивать continuation-строки к предыдущему DTO (например, stack trace).</li>
 * </ul>
 */
public interface LogParser {

    /**
     * Разбирает полный dump.
     *
     * <p>Контракт intentionally best-effort: шум kube и строки, которые не удалось
     * превратить в {@link PodLogDto}, не валят вызов целиком. Continuation-строки
     * после успешно распознанного JSON могут быть добавлены в поле
     * {@link PodLogDto#getStackTrace()} предыдущего DTO.
     *
     * @param rawDump сырой ответ {@code pods/log}; {@code null}/blank → пустой список
     * @return упорядоченный список распознанных JSON-событий
     */
    List<PodLogDto> parse(String rawDump);
}
