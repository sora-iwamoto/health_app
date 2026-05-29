package com.nimura.app.scoring;

public enum FatigueLevel {
    GOOD(0, 30),
    MILD(31, 60),
    MODERATE(61, 80),
    SEVERE(81, 100);

    public final int min;
    public final int max;

    FatigueLevel(int min, int max) {
        this.min = min;
        this.max = max;
    }

    public static FatigueLevel fromScore(int score) {
        if (score <= GOOD.max) return GOOD;
        if (score <= MILD.max) return MILD;
        if (score <= MODERATE.max) return MODERATE;
        return SEVERE;
    }
}
