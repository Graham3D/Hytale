package com.inigmasgames.persistentnpcs.ui;

/** Admission control, not an estimate of Hytale's undocumented shared atlas capacity. */
public record AppearanceUiAssetBudget(int maxVisibleDynamicImages, int maxSessionDynamicImages,
        long maxDecodedPixels, long maxEncodedBytes) {
    public static final int MAX_VISIBLE_CARDS = 20;
    public static final AppearanceUiAssetBudget PRODUCTION = new AppearanceUiAssetBudget(0, 0, 0, 0);

    public AppearanceUiAssetBudget {
        if (maxVisibleDynamicImages < 0 || maxSessionDynamicImages < 0
                || maxDecodedPixels < 0 || maxEncodedBytes < 0) throw new IllegalArgumentException("Negative asset budget");
    }

    public void requireUsage(int visibleCards, int dynamicImages, long pixels, long bytes) {
        if (visibleCards < 0 || visibleCards > MAX_VISIBLE_CARDS || dynamicImages < 0
                || pixels < 0 || bytes < 0 || dynamicImages > maxVisibleDynamicImages
                || dynamicImages > maxSessionDynamicImages || pixels > maxDecodedPixels
                || bytes > maxEncodedBytes) throw new IllegalStateException("Appearance dynamic images are disabled; static selectors only");
    }
}
