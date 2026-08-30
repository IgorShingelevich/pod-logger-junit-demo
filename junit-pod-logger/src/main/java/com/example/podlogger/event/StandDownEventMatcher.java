package com.example.podlogger.event;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

import com.example.podlogger.client.PodEventDto;

/**
 * Чистая функция: какие Events означают «стенд не работает».
 *
 * <p>Не ходит в Kubernetes. Наши маркеры {@code TestRunStarted}/{@code TestRunFinished}
 * всегда исключаются. Пустой список кодов/паттернов на входе заменяется дефолтом библиотеки,
 * а не трактуется как «не матчить ничего».
 */
public final class StandDownEventMatcher {

    /**
     * Утилитный класс, экземпляры не создаются.
     */
    private StandDownEventMatcher() {
    }

    /**
     * Match с дефолтными кодами и паттернами библиотеки.
     *
     * @param events Events поды
     * @return подмножество stand-down; пустой список если нет
     */
    public static List<PodEventDto> match(List<PodEventDto> events) {
        return match(events, PodEventReasons.DEFAULT_STAND_DOWN_CODES, PodEventReasons.DEFAULT_MESSAGE_PATTERNS);
    }

    /**
     * Match с явными списками. Пустые коллекции → дефолт библиотеки.
     *
     * @param events           Events поды
     * @param codes            allowlist {@code reason}/{@code code}
     * @param messagePatterns  подстроки reason/message
     * @return подмножество stand-down
     */
    public static List<PodEventDto> match(
            List<PodEventDto> events,
            Collection<String> codes,
            Collection<String> messagePatterns) {
        if (events == null || events.isEmpty()) {
            return List.of();
        }
        List<String> effectiveCodes = (codes == null || codes.isEmpty())
                ? PodEventReasons.DEFAULT_STAND_DOWN_CODES
                : List.copyOf(codes);
        List<String> effectivePatterns = (messagePatterns == null || messagePatterns.isEmpty())
                ? PodEventReasons.DEFAULT_MESSAGE_PATTERNS
                : List.copyOf(messagePatterns);
        List<PodEventDto> matched = new ArrayList<>();
        for (PodEventDto event : events) {
            if (event == null) {
                continue;
            }
            if (isLifecycle(event)) {
                continue;
            }
            if (codeMatches(event, effectiveCodes) || textMatches(event, effectivePatterns)) {
                matched.add(event);
            }
        }
        return matched;
    }

    /**
     * Contains (case-insensitive) любой из паттернов в тексте.
     * Используется и для Event.message, и для тела HTTP health.
     *
     * @param text             исходный текст
     * @param messagePatterns  подстроки; пустые → дефолт
     * @return {@code true} если хотя бы один паттерн найден
     */
    public static boolean matchesText(String text, Collection<String> messagePatterns) {
        if (text == null || text.isBlank()) {
            return false;
        }
        List<String> effectivePatterns = (messagePatterns == null || messagePatterns.isEmpty())
                ? PodEventReasons.DEFAULT_MESSAGE_PATTERNS
                : List.copyOf(messagePatterns);
        String lower = text.toLowerCase(Locale.ROOT);
        for (String pattern : effectivePatterns) {
            if (pattern != null && !pattern.isBlank() && lower.contains(pattern.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Наши lifecycle-маркеры не stand-down.
     *
     * @param event Event
     * @return {@code true} если code/reason из {@link PodEventReasons#LIFECYCLE_CODES}
     */
    private static boolean isLifecycle(PodEventDto event) {
        String code = firstNonBlank(event.getCode(), event.getReason());
        return code != null && PodEventReasons.LIFECYCLE_CODES.contains(code);
    }

    /**
     * Сравнение code/reason с allowlist без учёта регистра.
     *
     * @param event Event
     * @param codes ожидаемые коды
     * @return {@code true} при совпадении
     */
    private static boolean codeMatches(PodEventDto event, Collection<String> codes) {
        String code = firstNonBlank(event.getCode(), event.getReason());
        if (code == null) {
            return false;
        }
        for (String expected : codes) {
            if (expected != null && expected.equalsIgnoreCase(code)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Паттерн в reason/code или в message.
     *
     * @param event    Event
     * @param patterns подстроки
     * @return {@code true} при совпадении
     */
    private static boolean textMatches(PodEventDto event, Collection<String> patterns) {
        return matchesText(firstNonBlank(event.getReason(), event.getCode()), patterns)
                || matchesText(event.getMessage(), patterns);
    }

    /**
     * Первая непустая строка из двух.
     *
     * @param a предпочтительное значение
     * @param b запасное
     * @return строка или {@code null}
     */
    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        if (b != null && !b.isBlank()) {
            return b;
        }
        return null;
    }
}
