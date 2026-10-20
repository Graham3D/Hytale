package com.inigmasgames.hytalerpg.ui.model;

import java.util.List;

public record XpView(int level, long totalXp, long levelStartXp, long xpIntoLevel,
                     long xpToNext, double progress, List<Double> pipFill) {
    public XpView { pipFill = List.copyOf(pipFill); }
}
