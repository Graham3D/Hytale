package com.inigmasgames.hytalerpg.ui.inventory;

import java.util.*;

/** Placement metadata only. Native containers remain the sole owners of item payloads. */
public final class SpatialLayout {
    public record Size(int width, int height) {
        public Size { if (width <= 0 || height <= 0) throw new IllegalArgumentException("Positive footprint required"); }
    }
    public record Position(int x, int y) { }
    public record Entry(String id, Size size, Position position) {
        public Entry { Objects.requireNonNull(id); Objects.requireNonNull(size); Objects.requireNonNull(position); }
    }
    public record Grab(String id, int offsetX, int offsetY, long revision) { }
    public enum PlacementOutcome { MOVED, SWAPPED, STALE, OUT_OF_BOUNDS, AMBIGUOUS, NO_FIT }
    public record PlacementResult(PlacementOutcome outcome, List<Entry> changed) {
        public PlacementResult { changed = List.copyOf(changed); }
        public boolean accepted() { return outcome == PlacementOutcome.MOVED || outcome == PlacementOutcome.SWAPPED; }
    }
    private final int columns, rows;
    private final LinkedHashMap<String, Entry> entries = new LinkedHashMap<>();
    private long revision;

    public SpatialLayout(int columns, int rows) {
        if (columns <= 0 || rows <= 0 || (long) columns * rows > 4096)
            throw new IllegalArgumentException("Invalid bounded grid");
        this.columns = columns; this.rows = rows;
    }
    public long revision() { return revision; }
    public List<Entry> entries() { return List.copyOf(entries.values()); }
    public Optional<Entry> at(int x, int y) {
        return entries.values().stream().filter(e -> x >= e.position.x && y >= e.position.y
                && (long)x < (long)e.position.x + e.size.width && (long)y < (long)e.position.y + e.size.height).findFirst();
    }
    public boolean fits(Size size, Position target, String excluded) {
        if (target.x < 0 || target.y < 0 || (long)target.x + size.width > columns
                || (long)target.y + size.height > rows) return false;
        for (var e : entries.values()) {
            if (e.id.equals(excluded)) continue;
            if (target.x < e.position.x + e.size.width && target.x + size.width > e.position.x
                    && target.y < e.position.y + e.size.height && target.y + size.height > e.position.y) return false;
        }
        return true;
    }
    public Optional<Position> firstFit(Size size) {
        for (int y = 0; y <= rows - size.height; y++)
            for (int x = 0; x <= columns - size.width; x++) {
                var candidate = new Position(x, y);
                if (fits(size, candidate, null)) return Optional.of(candidate);
            }
        return Optional.empty();
    }
    public boolean add(String id, Size size, Position position) {
        Objects.requireNonNull(id);
        if (id.isBlank() || entries.containsKey(id) || !fits(size, position, null)) return false;
        entries.put(id, new Entry(id, size, position)); revision++; return true;
    }
    public Optional<Grab> grab(int x, int y) {
        return at(x, y).map(e -> new Grab(e.id, x - e.position.x, y - e.position.y, revision));
    }
    /** Cancelling simply discards Grab: no source was removed, reserved or changed. */
    public boolean move(Grab grab, int pointerX, int pointerY) {
        var e = entries.get(grab.id);
        if (e == null || grab.revision != revision || grab.offsetX < 0 || grab.offsetY < 0
                || grab.offsetX >= e.size.width || grab.offsetY >= e.size.height) return false;
        long x = (long)pointerX - grab.offsetX, y = (long)pointerY - grab.offsetY;
        if (x < 0 || y < 0 || x > columns || y > rows) return false;
        var target = new Position((int)x, (int)y);
        if (!fits(e.size, target, e.id)) return false;
        entries.put(e.id, new Entry(e.id, e.size, target)); revision++; return true;
    }

    /** Plans a single-cell-target move or an exact one-item swap before committing either rectangle. */
    public PlacementResult moveOrSwap(Grab grab, int pointerX, int pointerY) {
        if (grab == null) return rejected(PlacementOutcome.STALE);
        var source = entries.get(grab.id);
        if (source == null || grab.revision != revision || grab.offsetX < 0 || grab.offsetY < 0
                || grab.offsetX >= source.size.width || grab.offsetY >= source.size.height)
            return rejected(PlacementOutcome.STALE);
        long left = (long) pointerX - grab.offsetX, top = (long) pointerY - grab.offsetY;
        if (left < 0 || top < 0 || left + source.size.width > columns || top + source.size.height > rows)
            return rejected(PlacementOutcome.OUT_OF_BOUNDS);
        var target = new Position((int) left, (int) top);
        if (fits(source.size, target, source.id)) {
            var moved = new Entry(source.id, source.size, target);
            entries.put(source.id, moved); revision++;
            return new PlacementResult(PlacementOutcome.MOVED, List.of(moved));
        }
        var touched = entries.values().stream().filter(e -> !e.id.equals(source.id)
                && overlaps(target, source.size, e.position, e.size)).toList();
        if (touched.size() != 1) return rejected(touched.size() > 1 ? PlacementOutcome.AMBIGUOUS : PlacementOutcome.NO_FIT);
        var other = touched.getFirst();
        if (overlaps(target, source.size, source.position, other.size)
                || !fitsExcluding(source.size, target, source.id, other.id)
                || !fitsExcluding(other.size, source.position, source.id, other.id))
            return rejected(PlacementOutcome.NO_FIT);
        var moved = new Entry(source.id, source.size, target);
        var swapped = new Entry(other.id, other.size, source.position);
        entries.put(source.id, moved); entries.put(other.id, swapped); revision++;
        return new PlacementResult(PlacementOutcome.SWAPPED, List.of(moved, swapped));
    }

    /** Find the closest aligned rectangle to a release cell, retaining the grabbed-cell offset. */
    public PlacementResult moveOrSwapSnapped(Grab grab, int pointerX, int pointerY) {
        if (grab == null) return rejected(PlacementOutcome.STALE);
        var source = entries.get(grab.id);
        if (source == null || grab.revision != revision || grab.offsetX < 0 || grab.offsetY < 0
                || grab.offsetX >= source.size.width || grab.offsetY >= source.size.height)
            return rejected(PlacementOutcome.STALE);
        long desiredX = (long) pointerX - grab.offsetX;
        long desiredY = (long) pointerY - grab.offsetY;
        if (source.size.width > columns || source.size.height > rows)
            return rejected(PlacementOutcome.OUT_OF_BOUNDS);
        var candidates = new ArrayList<Position>();
        for (int y = 0; y <= rows - source.size.height; y++)
            for (int x = 0; x <= columns - source.size.width; x++)
                candidates.add(new Position(x, y));
        candidates.sort(Comparator.comparingLong((Position p) ->
                        distanceSquared(p, desiredX, desiredY))
                .thenComparingInt(Position::y).thenComparingInt(Position::x));
        for (var target : candidates) {
            // A blocked release must not report a successful move back to its source.
            if (target.equals(source.position) && (desiredX != target.x || desiredY != target.y)) continue;
            var result = moveOrSwap(grab, target.x + grab.offsetX, target.y + grab.offsetY);
            if (result.accepted()) return result;
        }
        return rejected(PlacementOutcome.NO_FIT);
    }

    private static long distanceSquared(Position candidate, long x, long y) {
        long dx = candidate.x - x, dy = candidate.y - y;
        return dx * dx + dy * dy;
    }

    private boolean fitsExcluding(Size size, Position target, String first, String second) {
        if (target.x < 0 || target.y < 0 || (long) target.x + size.width > columns
                || (long) target.y + size.height > rows) return false;
        for (var e : entries.values()) {
            if (e.id.equals(first) || e.id.equals(second)) continue;
            if (overlaps(target, size, e.position, e.size)) return false;
        }
        return true;
    }

    private static boolean overlaps(Position a, Size aSize, Position b, Size bSize) {
        return a.x < b.x + bSize.width && a.x + aSize.width > b.x
                && a.y < b.y + bSize.height && a.y + aSize.height > b.y;
    }

    private static PlacementResult rejected(PlacementOutcome outcome) {
        return new PlacementResult(outcome, List.of());
    }
}
