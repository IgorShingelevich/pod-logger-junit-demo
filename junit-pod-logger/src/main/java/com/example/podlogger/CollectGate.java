package com.example.podlogger;

/**
 * Единый gate для двух выходов runtime-логов: Allure-аттач и запись в SQLite.
 *
 * <p>Отдельного флага {@code persist} нет. Решение одно:
 * {@code shouldCollect = !collectOnFailOnly || failed}.
 *
 * <p>Для <em>упавшего</em> invocation CollectGate всегда {@code true}
 * (строки «fail + CollectGate false» не существует). События Kubernetes (Events)
 * этим классом не управляются: они обрабатываются только на fail отдельно.
 *
 * <p>Класс без состояния, экземпляры не создаются.
 */
public final class CollectGate {

    private CollectGate() {
    }

    /**
     * Решает, нужно ли снимать логи окна и отдавать их в Allure и SQLite.
     *
     * @param collectOnFailOnly значение атрибута {@link PodLogger#collectOnFailOnly()}
     *                          (или зеркала в {@link PodLoggerProperties})
     * @param failed            {@code true}, если текущий invocation завершился с исключением
     * @return {@code true}, если логи нужно собрать; {@code false} — пропустить оба выхода
     */
    public static boolean shouldCollect(boolean collectOnFailOnly, boolean failed) {
        return !collectOnFailOnly || failed;
    }
}
