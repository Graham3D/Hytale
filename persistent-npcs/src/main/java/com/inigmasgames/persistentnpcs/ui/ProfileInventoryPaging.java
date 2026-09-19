package com.inigmasgames.persistentnpcs.ui;

/** Presentation only. Never slices, replaces, or resizes an authoritative container. */
public record ProfileInventoryPaging(int capacity, int page) {
    public static final int PAGE_SIZE = 28;
    public ProfileInventoryPaging {
        if (capacity < 1 || page < 0 || page >= (capacity + PAGE_SIZE - 1) / PAGE_SIZE)
            throw new IllegalArgumentException("Invalid inventory page");
    }
    public int pageCount() { return (capacity + PAGE_SIZE - 1) / PAGE_SIZE; }
    public int firstSlot() { return page * PAGE_SIZE; }
    public int slotCount() { return Math.min(PAGE_SIZE, capacity - firstSlot()); }
    public int targetSlot(int visualSlot) {
        if (visualSlot < 0 || visualSlot >= slotCount())
            throw new IllegalArgumentException("Inventory target is outside the visible page");
        return firstSlot() + visualSlot;
    }
    public int shifted(int delta) { return Math.clamp(page + delta, 0, pageCount() - 1); }
    public String label() { return "PAGE " + (page + 1) + " / " + pageCount(); }
    public static void requireRevision(String supplied, long current) {
        if (!Long.toString(current).equals(supplied))
            throw new IllegalStateException("Inventory page changed; retry the move on the current page.");
    }
}
