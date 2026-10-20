package com.inigmasgames.hytalerpg.ui.inventory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** Read-only migration proposal; never changes an ItemContainer or creates recovery items. */
public final class SpatialMigrationPreflight {
    public enum Reason { UNMAPPED, NO_RECTANGLE }
    public record Input(short backingSlot, String baseItemId) { }
    public record Placed(short backingSlot, String baseItemId, SpatialLayout.Position position,
                         SpatialLayout.Size size) { }
    public record Overflow(short backingSlot, String baseItemId, Reason reason) { }
    public record Result(int catalogRevision, List<Placed> placed, List<Overflow> overflow) {
        public Result { placed = List.copyOf(placed); overflow = List.copyOf(overflow); }
    }

    private SpatialMigrationPreflight() { }

    public static Result plan(int columns, int rows, FootprintCatalog catalog, List<Input> inputs) {
        Objects.requireNonNull(catalog); Objects.requireNonNull(inputs);
        var slots = new HashSet<Short>();
        var layout = new SpatialLayout(columns, rows);
        var placed = new ArrayList<Placed>();
        var overflow = new ArrayList<Overflow>();
        for (var input : inputs) {
            Objects.requireNonNull(input);
            if (input.backingSlot < 0 || input.baseItemId == null || input.baseItemId.isBlank()
                    || !slots.add(input.backingSlot)) throw new IllegalArgumentException("Invalid or duplicate backing slot");
            var size = catalog.size(input.baseItemId);
            if (size == null) { overflow.add(new Overflow(input.backingSlot, input.baseItemId, Reason.UNMAPPED)); continue; }
            var position = layout.firstFit(size);
            if (position.isEmpty()) { overflow.add(new Overflow(input.backingSlot, input.baseItemId, Reason.NO_RECTANGLE)); continue; }
            String identity = "Slot" + input.backingSlot;
            if (!layout.add(identity, size, position.get())) throw new IllegalStateException("Preflight placement conflict");
            placed.add(new Placed(input.backingSlot, input.baseItemId, position.get(), size));
        }
        if (placed.size() + overflow.size() != inputs.size()) throw new IllegalStateException("Preflight conservation failure");
        return new Result(catalog.revision(), placed, overflow);
    }
}
