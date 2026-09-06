package com.inigmasgames.hytalerpg.ui;

import com.inigmasgames.hytalerpg.ui.model.XpView;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/** Canonical character-XP formula and presentation-only ten-pip projection. */
public final class CharacterXpProjectionService {
    public static final int LEVEL_CAP = 99;

    public long xpToNext(int level) {
        if (level < 1 || level >= LEVEL_CAP) return 0;
        double pressure = 1.0 + 5.0 * Math.pow(Math.max(0.0, level - 80.0) / 18.0, 3.0);
        double raw = 100.0 * Math.pow(level, 1.6) * pressure;
        return BigDecimal.valueOf(raw / 10.0).setScale(0, RoundingMode.HALF_UP).longValueExact() * 10L;
    }

    public long levelStartXp(int level) {
        if (level < 1 || level > LEVEL_CAP) throw new IllegalArgumentException("Level must be 1..99");
        long total = 0;
        for (int current = 1; current < level; current++) total = Math.addExact(total, xpToNext(current));
        return total;
    }

    public XpView project(long totalXp) {
        if (totalXp < 0) throw new IllegalArgumentException("Total XP cannot be negative");
        int level = 1;
        while (level < LEVEL_CAP && totalXp >= levelStartXp(level + 1)) level++;
        long start = levelStartXp(level);
        long next = xpToNext(level);
        long into = Math.max(0, totalXp - start);
        double progress = level == LEVEL_CAP ? 1.0 : clamp((double) into / next);
        List<Double> pips = new ArrayList<>(10);
        for (int index = 0; index < 10; index++) pips.add(clamp(10.0 * progress - index));
        return new XpView(level, totalXp, start, into, next, progress, pips);
    }

    /** Controlled presentation fixture; it never mutates or awards character XP. */
    public XpView fixturePercent(double percent) {
        if (!Double.isFinite(percent)) throw new IllegalArgumentException("XP fixture percent must be finite");
        double progress = clamp(percent / 100.0);
        List<Double> pips = new ArrayList<>(10);
        for (int index = 0; index < 10; index++) pips.add(clamp(10.0 * progress - index));
        return new XpView(1, Math.round(xpToNext(1) * progress), 0,
                Math.round(xpToNext(1) * progress), xpToNext(1), progress, pips);
    }

    private static double clamp(double value) { return Math.max(0.0, Math.min(1.0, value)); }
}
