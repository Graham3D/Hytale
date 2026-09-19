package com.inigmasgames.canvasui.api.editor;

import java.util.List;
import java.util.Locale;

/** Search-before-pagination projection for one typed editor library. */
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

    public record Page(List<CursorCanvasEditor.LibraryEntry> entries, int pageIndex,
                       int pageCount, int totalMatches) {
        public Page { entries = List.copyOf(entries); }
    }
}
