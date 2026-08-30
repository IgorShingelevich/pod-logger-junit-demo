package com.example.podlogger.event;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

import com.example.podlogger.client.PodEventDto;

/**
 * Pure function: which pod Events mean "the stand is down".
 */
public final class StandDownEventMatcher {

    private StandDownEventMatcher() {
    }

    public static List<PodEventDto> match(List<PodEventDto> events) {
        return match(events, PodEventReasons.DEFAULT_STAND_DOWN_CODES, PodEventReasons.DEFAULT_MESSAGE_PATTERNS);
    }

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

    private static boolean isLifecycle(PodEventDto event) {
        String code = firstNonBlank(event.getCode(), event.getReason());
        return code != null && PodEventReasons.LIFECYCLE_CODES.contains(code);
    }

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

    private static boolean textMatches(PodEventDto event, Collection<String> patterns) {
        return matchesText(firstNonBlank(event.getReason(), event.getCode()), patterns)
                || matchesText(event.getMessage(), patterns);
    }

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
