package com.example.podlogger;

/**
 * Single gate for Allure attach and SQLite persist.
 */
public final class CollectGate {

    private CollectGate() {
    }

    public static boolean shouldCollect(boolean collectOnFailOnly, boolean failed) {
        return !collectOnFailOnly || failed;
    }
}
