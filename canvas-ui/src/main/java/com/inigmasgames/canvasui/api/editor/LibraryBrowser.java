package com.inigmasgames.canvasui.api.editor;

import java.util.List;
import java.util.Locale;

/** Search-before-window projection for one typed editor library. */
public final class LibraryBrowser {
    private LibraryBrowser() { }

    public static Page project(List<CursorCanvasEditor.LibraryEntry> source, String query,
                               int requestedPage, int pageSize) {
        if (pageSize < 1) throw new IllegalArgumentException("pageSize must be positive");
        String needle = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
        List<CursorCanvasEditor.LibraryEntry> filtered = source.stream()
                .filter(entry -> needle.isEmpty() || entry.name().toLowerCase(Locale.ROOT).contains(needle))
                .toList();
        int pageCount = Math.max(1, (filtered.size() + pageSize - 1) / pageSize);
        int page = Math.max(0, Math.min(requestedPage, pageCount - 1));
        int start = Math.min(filtered.size(), page * pageSize);
        return new Page(filtered.subList(start, Math.min(filtered.size(), start + pageSize)),
                page, pageCount, filtered.size());
    }

    public static Window window(List<CursorCanvasEditor.LibraryEntry> source, String query,
                                int requestedOffset, int visibleRows) {
        if (visibleRows < 1) throw new IllegalArgumentException("visibleRows must be positive");
        String needle = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
        List<CursorCanvasEditor.LibraryEntry> filtered = source.stream()
                .filter(entry -> needle.isEmpty() || entry.name().toLowerCase(Locale.ROOT).contains(needle))
                .toList();
        int maximumOffset = Math.max(0, filtered.size() - visibleRows);
        int offset = Math.max(0, Math.min(requestedOffset, maximumOffset));
        int end = Math.min(filtered.size(), offset + visibleRows);
        double thumbFraction = filtered.isEmpty() ? 1.0 : Math.min(1.0, visibleRows / (double) filtered.size());
        double progress = maximumOffset == 0 ? 0.0 : offset / (double) maximumOffset;
        return new Window(filtered.subList(offset, end), offset, maximumOffset, filtered.size(),
                thumbFraction, progress);
    }

    public record Page(List<CursorCanvasEditor.LibraryEntry> entries, int pageIndex,
                       int pageCount, int totalMatches) {
        public Page { entries = List.copyOf(entries); }
    }

    public record Window(List<CursorCanvasEditor.LibraryEntry> entries, int offset,
                         int maximumOffset, int totalMatches, double thumbFraction,
                         double progress) {
        public Window { entries = List.copyOf(entries); }
    }
}
