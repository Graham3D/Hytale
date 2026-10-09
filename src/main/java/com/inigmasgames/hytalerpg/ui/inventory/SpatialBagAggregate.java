package com.inigmasgames.hytalerpg.ui.inventory;

import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.inigmasgames.hytalerpg.gear.GearNativeItems;
import org.bson.BsonDocument;
import org.bson.BsonArray;
import org.bson.BsonInt32;
import org.bson.BsonInt64;
import org.bson.BsonString;
import java.util.*;

/** One private owner. No native ItemContainer ever receives this aggregate. */
public final class SpatialBagAggregate {
    public enum Outcome { ACCEPTED, NO_FIT, UNMAPPED, STALE, MISSING, INVALID_QUANTITY, REPLAY_MISMATCH }
    public record Entry(UUID id, String payloadJson, SpatialLayout.Size size, SpatialLayout.Position position) {
        public Entry {
            Objects.requireNonNull(id); Objects.requireNonNull(payloadJson);
            Objects.requireNonNull(size); Objects.requireNonNull(position);
            if (ItemStack.isEmpty(SpatialBagAggregate.payload(payloadJson))) throw new IllegalArgumentException("Empty spatial entry");
        }
        public ItemStack payload() { return SpatialBagAggregate.payload(payloadJson); }
    }
    public record Receipt(UUID operationId, String request, Outcome outcome, long revision, UUID entryId) { }
    public record Result(SpatialBagAggregate bag, Receipt receipt) {
        public boolean accepted() { return receipt.outcome() == Outcome.ACCEPTED; }
    }
    public record OfferedItem(UUID entryId, ItemStack payload) {
        public OfferedItem { Objects.requireNonNull(entryId); Objects.requireNonNull(payload); }
    }

    private final UUID owner;
    private final int catalogRevision;
    private final long revision;
    private final Map<UUID, Entry> entries;
    private final Map<UUID, Receipt> receipts;

    public SpatialBagAggregate(UUID owner, int catalogRevision) {
        this(owner, catalogRevision, 0, Map.of(), Map.of());
    }
    private SpatialBagAggregate(UUID owner, int catalogRevision, long revision,
                                Map<UUID, Entry> entries, Map<UUID, Receipt> receipts) {
        this.owner = Objects.requireNonNull(owner);
        if (catalogRevision <= 0 || revision < 0) throw new IllegalArgumentException("Invalid bag revision");
        this.catalogRevision = catalogRevision;
        this.revision = revision;
        this.entries = Map.copyOf(entries);
        this.receipts = Map.copyOf(receipts);
        validate(this.entries.values());
    }
    public UUID owner() { return owner; }
    public int catalogRevision() { return catalogRevision; }
    public long revision() { return revision; }
    public Collection<Entry> entries() { return entries.values(); }
    public Optional<Entry> entry(UUID id) { return Optional.ofNullable(entries.get(id)); }
    public Collection<Receipt> receipts() { return receipts.values(); }

    /** One BSON value for the future player component; no second mutable file is written. */
    public BsonDocument toBson() {
        var root = new BsonDocument("Owner", new BsonString(owner.toString()))
                .append("CatalogRevision", new BsonInt32(catalogRevision))
                .append("Revision", new BsonInt64(revision));
        var savedEntries = new BsonArray();
        for (var entry : entries.values().stream().sorted(Comparator.comparing(e -> e.id().toString())).toList()) savedEntries.add(new BsonDocument("Id", new BsonString(entry.id().toString()))
                .append("Payload", BsonDocument.parse(entry.payloadJson()))
                .append("Width", new BsonInt32(entry.size().width()))
                .append("Height", new BsonInt32(entry.size().height()))
                .append("X", new BsonInt32(entry.position().x()))
                .append("Y", new BsonInt32(entry.position().y())));
        root.append("Entries", savedEntries);
        var savedReceipts = new BsonArray();
        for (var receipt : receipts.values().stream().sorted(Comparator.comparing(r -> r.operationId().toString())).toList()) savedReceipts.add(new BsonDocument("Operation", new BsonString(receipt.operationId().toString()))
                .append("Request", new BsonString(receipt.request()))
                .append("Outcome", new BsonString(receipt.outcome().name()))
                .append("Revision", new BsonInt64(receipt.revision()))
                .append("Entry", new BsonString(receipt.entryId() == null ? "" : receipt.entryId().toString())));
        return root.append("Receipts", savedReceipts);
    }

    public static SpatialBagAggregate fromBson(BsonDocument root, FootprintCatalog catalog) {
        UUID owner = UUID.fromString(root.getString("Owner").getValue());
        int catalogRevision = root.getInt32("CatalogRevision").getValue();
        if (catalogRevision != catalog.revision()
                && !(catalogRevision == 2 && catalog.revision() == 3))
            throw new IllegalStateException("Spatial catalog migration required");
        long revision = savedRevision(root);
        var entries = new LinkedHashMap<UUID, Entry>();
        for (var value : root.getArray("Entries")) {
            var saved = value.asDocument();
            UUID id = UUID.fromString(saved.getString("Id").getValue());
            String payload = saved.getDocument("Payload").toJson();
            var size = new SpatialLayout.Size(saved.getInt32("Width").getValue(), saved.getInt32("Height").getValue());
            ItemStack stack = payload(payload);
            String nativeId = GearNativeItems.nativeId(stack.getItemId());
            var currentSize = catalog.size(nativeId);
            var previousSize = catalog.previousSize(nativeId);
            if (currentSize == null || !(size.equals(currentSize)
                    || size.equals(previousSize) && catalogRevision >= 2))
                throw new IllegalStateException("Saved footprint differs from catalog");
            var position = new SpatialLayout.Position(saved.getInt32("X").getValue(), saved.getInt32("Y").getValue());
            if (entries.putIfAbsent(id, new Entry(id, payload, size, position)) != null)
                throw new IllegalStateException("Duplicate spatial entry");
        }
        var receipts = new LinkedHashMap<UUID, Receipt>();
        for (var value : root.getArray("Receipts")) {
            var saved = value.asDocument();
            UUID id = UUID.fromString(saved.getString("Operation").getValue());
            String entry = saved.getString("Entry").getValue();
            var receipt = new Receipt(id, saved.getString("Request").getValue(),
                    Outcome.valueOf(saved.getString("Outcome").getValue()),
                    savedRevision(saved), entry.isEmpty() ? null : UUID.fromString(entry));
            if (receipt.revision() > revision || receipts.putIfAbsent(id, receipt) != null)
                throw new IllegalStateException("Invalid spatial receipt");
        }
        // Revision 2 entries retain their identity, payload, and receipts. Repack
        // against the artist's new rectangles only if every entry fits. A full
        // bag keeps its old rectangles rather than losing an item or blocking
        // player load; new pickups still use revision 3 dimensions.
        Map<UUID, Entry> migrated = null;
        if (catalogRevision == 2) {
            var resized = new LinkedHashMap<UUID, Entry>();
            for (var entry : entries.values()) {
                String nativeId = GearNativeItems.nativeId(entry.payload().getItemId());
                resized.put(entry.id(), new Entry(entry.id(), entry.payloadJson(),
                        catalog.size(nativeId), entry.position()));
            }
            migrated = reflow(resized.values());
        }
        if (migrated == null) migrated = reflow(entries.values());
        if (migrated == null)
            throw new IllegalStateException("Saved spatial bag cannot fit revised grid");
        return new SpatialBagAggregate(owner, catalog.revision(), revision, migrated, receipts);
    }

    private static Map<UUID, Entry> reflow(Collection<Entry> entries) {
        var migrated = new LinkedHashMap<UUID, Entry>();
        var layout = new SpatialLayout(InventoryGridGeometry.COLUMNS, InventoryGridGeometry.ROWS);
        var ordered = entries.stream().sorted(Comparator
                .comparingInt((Entry e) -> e.position().y())
                .thenComparingInt(e -> e.position().x())
                .thenComparing(e -> e.id().toString())).toList();
        for (var entry : ordered) {
            if (layout.add(entry.id().toString(), entry.size(), entry.position()))
                migrated.put(entry.id(), entry);
        }
        for (var entry : ordered) {
            if (migrated.containsKey(entry.id())) continue;
            var position = layout.firstFit(entry.size()).orElse(null);
            if (position == null) return null;
            if (!layout.add(entry.id().toString(), entry.size(), position))
                throw new IllegalStateException("Spatial reflow failed");
            migrated.put(entry.id(), new Entry(entry.id(), entry.payloadJson(), entry.size(), position));
        }
        return migrated;
    }

    /** Hytale's player save may normalize small BSON INT64 values to INT32. */
    private static long savedRevision(BsonDocument document) {
        var value = document.get("Revision");
        if (value instanceof BsonInt64 number) return number.getValue();
        if (value instanceof BsonInt32 number) return number.getValue();
        throw new IllegalArgumentException("Invalid spatial revision type: " + (value == null ? "missing" : value.getBsonType()));
    }

    /** An issuer must authenticate ownership before calling this. A source is not debited here. */
    public Result offer(UUID operationId, long expectedRevision, ItemStack stack, FootprintCatalog catalog) {
        requireCatalog(catalog);
        String payload = encode(stack);
        String request = "offer:" + payload + ":" + expectedRevision;
        var replay = replay(operationId, request);
        if (replay != null) return replay;
        if (expectedRevision != revision) return record(operationId, request, Outcome.STALE, null, entries);
        var size = catalog.size(GearNativeItems.nativeId(stack.getItemId()));
        if (size == null) return record(operationId, request, Outcome.UNMAPPED, null, entries);
        var layout = layout();
        var position = layout.firstFit(size);
        if (position.isEmpty()) return record(operationId, request, Outcome.NO_FIT, null, entries);
        var next = new LinkedHashMap<>(entries);
        next.put(operationId, new Entry(operationId, payload, size, position.get()));
        return record(operationId, request, Outcome.ACCEPTED, operationId, next);
    }

    /** Plan an equipment return at the chosen rectangle in one bag revision. */
    public Result offerAt(UUID operationId, long expectedRevision, ItemStack stack,
                          FootprintCatalog catalog, int x, int y) {
        return offerAt(operationId, operationId, expectedRevision, stack, catalog, x, y);
    }
    public Result offerAt(UUID operationId, UUID entryId, long expectedRevision, ItemStack stack,
                          FootprintCatalog catalog, int x, int y) {
        requireCatalog(catalog);
        String payload = encode(stack);
        String request = "offerAt:" + entryId + ":" + payload + ":" + expectedRevision + ":" + x + ":" + y;
        var replay = replay(operationId, request);
        if (replay != null) return replay;
        if (expectedRevision != revision) return record(operationId, request, Outcome.STALE, null, entries);
        if (entries.containsKey(entryId)) return record(operationId, request, Outcome.REPLAY_MISMATCH, null, entries);
        var size = catalog.size(GearNativeItems.nativeId(stack.getItemId()));
        if (size == null) return record(operationId, request, Outcome.UNMAPPED, null, entries);
        var position = new SpatialLayout.Position(x, y);
        var layout = layout();
        if (!layout.fits(size, position, null)) return record(operationId, request, Outcome.NO_FIT, null, entries);
        var next = new LinkedHashMap<>(entries);
        next.put(entryId, new Entry(entryId, payload, size, position));
        return record(operationId, request, Outcome.ACCEPTED, entryId, next);
    }

    /** A gear return prefers the released cell, then a free rectangle, then one atomic repack. */
    public Result offerForEquipmentReturn(UUID operationId, UUID entryId, long expectedRevision,
                                          ItemStack stack, FootprintCatalog catalog, int x, int y) {
        Result planned;
        if (x >= 0 && y >= 0) {
            planned = offerAt(operationId, entryId, expectedRevision, stack, catalog, x, y);
            if (planned.receipt().outcome() != Outcome.NO_FIT
                    || x >= InventoryGridGeometry.COLUMNS || y >= InventoryGridGeometry.ROWS)
                return planned;
        }
        planned = offerAll(operationId, expectedRevision, List.of(new OfferedItem(entryId, stack)), catalog);
        if (planned.receipt().outcome() != Outcome.NO_FIT) return planned;
        return offerRepacked(operationId, entryId, expectedRevision, stack, catalog);
    }

    /** Repack only as a candidate: a rejected fit never moves an existing entry. */
    private Result offerRepacked(UUID operationId, UUID entryId, long expectedRevision,
                                 ItemStack stack, FootprintCatalog catalog) {
        requireCatalog(catalog);
        String payload = encode(stack);
        String request = "offerRepacked:" + entryId + ':' + payload + ':' + expectedRevision;
        var replay = replay(operationId, request);
        if (replay != null) return replay;
        if (expectedRevision != revision) return record(operationId, request, Outcome.STALE, null, entries);
        if (entries.containsKey(entryId)) return record(operationId, request, Outcome.REPLAY_MISMATCH, null, entries);
        var size = catalog.size(GearNativeItems.nativeId(stack.getItemId()));
        if (size == null) return record(operationId, request, Outcome.UNMAPPED, null, entries);
        var ordered = new ArrayList<Entry>(entries.values());
        ordered.add(new Entry(entryId, payload, size, new SpatialLayout.Position(0, 0)));
        ordered.sort(Comparator.comparingInt((Entry entry) -> entry.size().width() * entry.size().height())
                .reversed().thenComparing(entry -> entry.id().toString()));
        var layout = new SpatialLayout(InventoryGridGeometry.COLUMNS, InventoryGridGeometry.ROWS);
        var next = new LinkedHashMap<UUID, Entry>();
        for (var entry : ordered) {
            var position = layout.firstFit(entry.size());
            if (position.isEmpty()) return record(operationId, request, Outcome.NO_FIT, null, entries);
            if (!layout.add(entry.id().toString(), entry.size(), position.get()))
                throw new IllegalStateException("Equipment return repack changed unexpectedly");
            next.put(entry.id(), new Entry(entry.id(), entry.payloadJson(), entry.size(), position.get()));
        }
        return record(operationId, request, Outcome.ACCEPTED, entryId, next);
    }

    /** All-or-nothing pickup admission: fill compatible stacks before claiming new cells. */
    public Result offerStacking(UUID operationId, long expectedRevision, ItemStack stack, FootprintCatalog catalog) {
        requireCatalog(catalog);
        String payload = encode(stack);
        String request = "offerStacking:" + payload + ":" + expectedRevision;
        var replay = replay(operationId, request);
        if (replay != null) return replay;
        if (expectedRevision != revision) return record(operationId, request, Outcome.STALE, null, entries);
        var size = catalog.size(GearNativeItems.nativeId(stack.getItemId()));
        if (size == null) return record(operationId, request, Outcome.UNMAPPED, null, entries);
        int maximum = stack.getItem().getMaxStack();
        if (maximum < 1 || stack.getQuantity() < 1)
            return record(operationId, request, Outcome.INVALID_QUANTITY, null, entries);
        var next = new LinkedHashMap<>(entries);
        int remaining = stack.getQuantity();
        UUID firstTarget = null;
        for (var existing : entries.values().stream().sorted(Comparator
                .comparingInt((Entry entry) -> entry.position().y())
                .thenComparingInt(entry -> entry.position().x())
                .thenComparing(entry -> entry.id().toString())).toList()) {
            var current = existing.payload();
            if (!ItemStack.isStackableWith(current, stack)) continue;
            int accepted = Math.min(remaining, Math.max(0, current.getItem().getMaxStack() - current.getQuantity()));
            if (accepted <= 0) continue;
            next.put(existing.id(), new Entry(existing.id(), encode(current.withQuantity(current.getQuantity() + accepted)),
                    existing.size(), existing.position()));
            if (firstTarget == null) firstTarget = existing.id();
            remaining -= accepted;
            if (remaining == 0) break;
        }
        var layout = layout();
        int ordinal = 0;
        while (remaining > 0) {
            UUID id = ordinal == 0 ? operationId : UUID.nameUUIDFromBytes(
                    (operationId + ":" + ordinal).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            if (next.containsKey(id)) return record(operationId, request, Outcome.REPLAY_MISMATCH, null, entries);
            var position = layout.firstFit(size);
            if (position.isEmpty()) return record(operationId, request, Outcome.NO_FIT, null, entries);
            int quantity = Math.min(remaining, maximum);
            if (!layout.add(id.toString(), size, position.get())) throw new IllegalStateException("Stack admission layout changed");
            next.put(id, new Entry(id, encode(stack.withQuantity(quantity)), size, position.get()));
            if (firstTarget == null) firstTarget = id;
            remaining -= quantity;
            ordinal++;
        }
        return record(operationId, request, Outcome.ACCEPTED, firstTarget, next);
    }

    /** Preflight a complete import or displacement set. A failure leaves every entry unchanged. */
    public Result offerAll(UUID operationId, long expectedRevision, List<OfferedItem> offered,
                           FootprintCatalog catalog) {
        requireCatalog(catalog);
        Objects.requireNonNull(offered);
        if (offered.isEmpty()) throw new IllegalArgumentException("Empty spatial offer");
        var sorted = offered.stream().sorted(Comparator.comparing(item -> item.entryId().toString())).toList();
        StringBuilder request = new StringBuilder("offerAll:").append(expectedRevision);
        for (var item : sorted) request.append(':').append(item.entryId()).append(':').append(encode(item.payload()));
        var replay = replay(operationId, request.toString());
        if (replay != null) return replay;
        if (expectedRevision != revision) return record(operationId, request.toString(), Outcome.STALE, null, entries);
        var next = new LinkedHashMap<>(entries);
        var layout = layout();
        for (var item : sorted) {
            if (next.containsKey(item.entryId()))
                return record(operationId, request.toString(), Outcome.REPLAY_MISMATCH, null, entries);
            var size = catalog.size(GearNativeItems.nativeId(item.payload().getItemId()));
            if (size == null) return record(operationId, request.toString(), Outcome.UNMAPPED, null, entries);
            var position = layout.firstFit(size);
            if (position.isEmpty()) return record(operationId, request.toString(), Outcome.NO_FIT, null, entries);
            if (!layout.add(item.entryId().toString(), size, position.get()))
                throw new IllegalStateException("Spatial preflight changed unexpectedly");
            next.put(item.entryId(), new Entry(item.entryId(), encode(item.payload()), size, position.get()));
        }
        return record(operationId, request.toString(), Outcome.ACCEPTED, null, next);
    }

    /** Candidate withdrawal; the transfer coordinator must prove destination custody before publishing. */
    public Result withdraw(UUID operationId, long expectedRevision, UUID entryId, String expectedPayloadJson) {
        String request = "withdraw:" + entryId + ':' + expectedPayloadJson + ':' + expectedRevision;
        var replay = replay(operationId, request);
        if (replay != null) return replay;
        if (expectedRevision != revision) return record(operationId, request, Outcome.STALE, entryId, entries);
        var source = entries.get(entryId);
        if (source == null || !source.payloadJson().equals(expectedPayloadJson))
            return record(operationId, request, Outcome.MISSING, entryId, entries);
        var next = new LinkedHashMap<>(entries);
        next.remove(entryId);
        return record(operationId, request, Outcome.ACCEPTED, entryId, next);
    }

    /** Candidate quantity withdrawal; the caller must save before publishing a world source. */
    public Result withdrawQuantity(UUID operationId, long expectedRevision, UUID entryId,
                                   String expectedPayloadJson, int quantity) {
        String request="withdrawQuantity:"+entryId+":"+expectedPayloadJson+":"+quantity+":"+expectedRevision;
        var replay=replay(operationId,request);
        if(replay!=null)return replay;
        if(expectedRevision!=revision)return record(operationId,request,Outcome.STALE,entryId,entries);
        var source=entries.get(entryId);
        if(source==null||!source.payloadJson().equals(expectedPayloadJson))
            return record(operationId,request,Outcome.MISSING,entryId,entries);
        int available=source.payload().getQuantity();
        if(quantity<1||quantity>available)
            return record(operationId,request,Outcome.INVALID_QUANTITY,entryId,entries);
        var next=new LinkedHashMap<>(entries);
        if(quantity==available)next.remove(entryId);
        else next.put(entryId,new Entry(entryId,
                encode(source.payload().withQuantity(available-quantity)),source.size(),source.position()));
        return record(operationId,request,Outcome.ACCEPTED,entryId,next);
    }

    /** Atomically remove one bag entry and fit all displaced equipment into its freed space. */
    public Result exchange(UUID operationId, long expectedRevision, UUID sourceId,
                           String expectedPayloadJson, List<OfferedItem> displaced,
                           FootprintCatalog catalog) {
        requireCatalog(catalog);
        Objects.requireNonNull(displaced);
        var sorted = displaced.stream().sorted(Comparator.comparing(item -> item.entryId().toString())).toList();
        var request = new StringBuilder("exchange:").append(sourceId).append(':')
                .append(expectedPayloadJson).append(':').append(expectedRevision);
        for (var item : sorted) request.append(':').append(item.entryId()).append(':').append(encode(item.payload()));
        var replay = replay(operationId, request.toString());
        if (replay != null) return replay;
        if (expectedRevision != revision)
            return record(operationId, request.toString(), Outcome.STALE, sourceId, entries);
        var source = entries.get(sourceId);
        if (source == null || !source.payloadJson().equals(expectedPayloadJson))
            return record(operationId, request.toString(), Outcome.MISSING, sourceId, entries);
        var next = new LinkedHashMap<>(entries);
        next.remove(sourceId);
        var layout = new SpatialLayout(InventoryGridGeometry.COLUMNS, InventoryGridGeometry.ROWS);
        for (var entry : next.values())
            if (!layout.add(entry.id().toString(), entry.size(), entry.position()))
                throw new IllegalStateException("Invalid existing bag layout");
        for (var item : sorted) {
            if (next.containsKey(item.entryId()) || item.entryId().equals(sourceId))
                return record(operationId, request.toString(), Outcome.REPLAY_MISMATCH, sourceId, entries);
            var size = catalog.size(GearNativeItems.nativeId(item.payload().getItemId()));
            if (size == null) return record(operationId, request.toString(), Outcome.UNMAPPED, sourceId, entries);
            var position = layout.firstFit(size);
            if (position.isEmpty()) return record(operationId, request.toString(), Outcome.NO_FIT, sourceId, entries);
            if (!layout.add(item.entryId().toString(), size, position.get()))
                throw new IllegalStateException("Spatial exchange layout changed unexpectedly");
            next.put(item.entryId(), new Entry(item.entryId(), encode(item.payload()), size, position.get()));
        }
        return record(operationId, request.toString(), Outcome.ACCEPTED, sourceId, next);
    }

    /** Candidate reverse export; the coordinator must preflight every native destination first. */
    public Result clearForExport(UUID operationId, long expectedRevision, Map<UUID,String> expectedPayloads) {
        Objects.requireNonNull(expectedPayloads);
        var ordered=expectedPayloads.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList();
        var request=new StringBuilder("export:").append(expectedRevision);
        for(var item:ordered)request.append(':').append(item.getKey()).append(':').append(item.getValue());
        var replay=replay(operationId,request.toString());
        if(replay!=null)return replay;
        if(expectedRevision!=revision)return record(operationId,request.toString(),Outcome.STALE,null,entries);
        if(expectedPayloads.size()!=entries.size()||ordered.stream().anyMatch(item->{
            var current=entries.get(item.getKey());return current==null||!current.payloadJson().equals(item.getValue());
        }))return record(operationId,request.toString(),Outcome.MISSING,null,entries);
        return record(operationId,request.toString(),Outcome.ACCEPTED,null,Map.of());
    }

    /** Deterministic private sort. Rejection preserves the previous placement and payloads. */
    public Result sort(UUID operationId, long expectedRevision) {
        String request = "sort:" + expectedRevision;
        var replay = replay(operationId, request);
        if (replay != null) return replay;
        if (expectedRevision != revision) return record(operationId, request, Outcome.STALE, null, entries);
        var ordered = entries.values().stream().sorted(Comparator
                .comparingInt((Entry entry) -> entry.size().width() * entry.size().height()).reversed()
                .thenComparing(entry -> entry.payload().getItemId())
                .thenComparing(entry -> entry.id().toString())).toList();
        var layout = new SpatialLayout(InventoryGridGeometry.COLUMNS, InventoryGridGeometry.ROWS);
        var next = new LinkedHashMap<UUID, Entry>();
        for (var entry : ordered) {
            var position = layout.firstFit(entry.size());
            if (position.isEmpty()) return record(operationId, request, Outcome.NO_FIT, null, entries);
            if (!layout.add(entry.id().toString(), entry.size(), position.get()))
                throw new IllegalStateException("Spatial sort changed unexpectedly");
            next.put(entry.id(), new Entry(entry.id(), entry.payloadJson(), entry.size(), position.get()));
        }
        return record(operationId, request, Outcome.ACCEPTED, null, next);
    }

    /** In-bag equivalent of native stack consolidation; no external container is involved. */
    public Result consolidate(UUID operationId, long expectedRevision) {
        String request = "consolidate:" + expectedRevision;
        var replay = replay(operationId, request);
        if (replay != null) return replay;
        if (expectedRevision != revision) return record(operationId, request, Outcome.STALE, null, entries);
        var ordered = entries.values().stream().sorted(Comparator
                .comparingInt((Entry entry) -> entry.position().y())
                .thenComparingInt(entry -> entry.position().x())
                .thenComparing(entry -> entry.id().toString())).toList();
        var next = new LinkedHashMap<>(entries);
        boolean changed = false;
        for (int i = 0; i < ordered.size(); i++) {
            var target = next.get(ordered.get(i).id());
            if (target == null) continue;
            var a = target.payload();
            int limit = a.getItem().getMaxStack();
            if (a.getQuantity() >= limit) continue;
            for (int j = i + 1; j < ordered.size() && a.getQuantity() < limit; j++) {
                var source = next.get(ordered.get(j).id());
                if (source == null) continue;
                var b = source.payload();
                if (!ItemStack.isStackableWith(a, b)) continue;
                int amount = Math.min(limit - a.getQuantity(), b.getQuantity());
                if (amount <= 0) continue;
                a = a.withQuantity(a.getQuantity() + amount);
                next.put(target.id(), new Entry(target.id(), encode(a), target.size(), target.position()));
                if (amount == b.getQuantity()) next.remove(source.id());
                else next.put(source.id(), new Entry(source.id(), encode(b.withQuantity(b.getQuantity() - amount)),
                        source.size(), source.position()));
                changed = true;
            }
        }
        return record(operationId, request, changed ? Outcome.ACCEPTED : Outcome.NO_FIT, null,
                changed ? next : entries);
    }

    /** A click or native cursor intent only supplies cells; this aggregate resolves the item. */
    public Result move(UUID operationId, long expectedRevision, UUID entryId,
                       int sourceX, int sourceY, int targetX, int targetY) {
        String request = "move:" + entryId + ":" + sourceX + ":" + sourceY + ":" + targetX + ":" + targetY + ":" + expectedRevision;
        var replay = replay(operationId, request);
        if (replay != null) return replay;
        if (expectedRevision != revision) return record(operationId, request, Outcome.STALE, entryId, entries);
        var source = entries.get(entryId);
        if (source == null) return record(operationId, request, Outcome.MISSING, entryId, entries);
        var layout = layout();
        var grab = layout.grab(sourceX, sourceY).orElse(null);
        if (grab == null || !grab.id().equals(entryId.toString()))
            return record(operationId, request, Outcome.MISSING, entryId, entries);
        var placement = layout.moveOrSwapSnapped(grab, targetX, targetY);
        if (!placement.accepted()) return record(operationId, request, Outcome.NO_FIT, entryId, entries);
        var next = new LinkedHashMap<>(entries);
        for (var changed : placement.changed()) {
            UUID id = UUID.fromString(changed.id());
            var prior = next.get(id);
            next.put(id, new Entry(id, prior.payloadJson(), prior.size(), changed.position()));
        }
        return record(operationId, request, Outcome.ACCEPTED, entryId, next);
    }

    public Result split(UUID operationId, long expectedRevision, UUID sourceId, int quantity) {
        String request = "split:" + sourceId + ":" + quantity + ":" + expectedRevision;
        var replay = replay(operationId, request);
        if (replay != null) return replay;
        if (expectedRevision != revision) return record(operationId, request, Outcome.STALE, sourceId, entries);
        var source = entries.get(sourceId);
        if (source == null) return record(operationId, request, Outcome.MISSING, sourceId, entries);
        var stack = source.payload();
        if (quantity <= 0 || quantity >= stack.getQuantity())
            return record(operationId, request, Outcome.INVALID_QUANTITY, sourceId, entries);
        var position = layout().firstFit(source.size());
        if (position.isEmpty()) return record(operationId, request, Outcome.NO_FIT, sourceId, entries);
        var next = new LinkedHashMap<>(entries);
        next.put(sourceId, new Entry(sourceId, encode(stack.withQuantity(stack.getQuantity() - quantity)),
                source.size(), source.position()));
        next.put(operationId, new Entry(operationId, encode(stack.withQuantity(quantity)), source.size(), position.get()));
        return record(operationId, request, Outcome.ACCEPTED, operationId, next);
    }

    /** Place a held partial stack into an empty rectangle or a compatible stack. */
    public Result placeQuantity(UUID operationId, long expectedRevision, UUID sourceId,
                                int quantity, int targetX, int targetY) {
        String request="placeQuantity:"+sourceId+":"+quantity+":"+targetX+":"+targetY+":"+expectedRevision;
        var replay=replay(operationId,request);
        if(replay!=null)return replay;
        if(expectedRevision!=revision)return record(operationId,request,Outcome.STALE,sourceId,entries);
        var source=entries.get(sourceId);
        if(source==null)return record(operationId,request,Outcome.MISSING,sourceId,entries);
        var sourceStack=source.payload();
        if(quantity<1||quantity>=sourceStack.getQuantity())
            return record(operationId,request,Outcome.INVALID_QUANTITY,sourceId,entries);
        var layout=layout();
        var target=layout.at(targetX,targetY).orElse(null);
        var next=new LinkedHashMap<>(entries);
        if(target!=null){
            if(target.id().equals(sourceId.toString()))return record(operationId,request,Outcome.NO_FIT,sourceId,entries);
            UUID targetId=UUID.fromString(target.id());
            var targetEntry=entries.get(targetId);
            var targetStack=targetEntry.payload();
            if(!ItemStack.isStackableWith(sourceStack,targetStack)
                    ||targetStack.getQuantity()+quantity>targetStack.getItem().getMaxStack())
                return record(operationId,request,Outcome.NO_FIT,sourceId,entries);
            next.put(targetId,new Entry(targetId,encode(targetStack.withQuantity(targetStack.getQuantity()+quantity)),
                    targetEntry.size(),targetEntry.position()));
        }else{
            var position=new SpatialLayout.Position(targetX,targetY);
            if(!layout.add(operationId.toString(),source.size(),position))
                return record(operationId,request,Outcome.NO_FIT,sourceId,entries);
            next.put(operationId,new Entry(operationId,encode(sourceStack.withQuantity(quantity)),source.size(),position));
        }
        next.put(sourceId,new Entry(sourceId,encode(sourceStack.withQuantity(sourceStack.getQuantity()-quantity)),
                source.size(),source.position()));
        return record(operationId,request,Outcome.ACCEPTED,sourceId,next);
    }

    public Result merge(UUID operationId, long expectedRevision, UUID sourceId, UUID targetId) {
        String request = "merge:" + sourceId + ":" + targetId + ":" + expectedRevision;
        var replay = replay(operationId, request);
        if (replay != null) return replay;
        if (expectedRevision != revision) return record(operationId, request, Outcome.STALE, sourceId, entries);
        var source = entries.get(sourceId);
        var target = entries.get(targetId);
        if (source == null || target == null || sourceId.equals(targetId))
            return record(operationId, request, Outcome.MISSING, sourceId, entries);
        var a = source.payload(); var b = target.payload();
        if (!ItemStack.isStackableWith(a, b)) return record(operationId, request, Outcome.INVALID_QUANTITY, sourceId, entries);
        int limit = b.getItem().getMaxStack();
        int accepted = Math.min(a.getQuantity(), limit - b.getQuantity());
        if (accepted <= 0) return record(operationId, request, Outcome.NO_FIT, sourceId, entries);
        var next = new LinkedHashMap<>(entries);
        next.put(targetId, new Entry(targetId, encode(b.withQuantity(b.getQuantity() + accepted)), target.size(), target.position()));
        if (accepted == a.getQuantity()) next.remove(sourceId);
        else next.put(sourceId, new Entry(sourceId, encode(a.withQuantity(a.getQuantity() - accepted)), source.size(), source.position()));
        return record(operationId, request, Outcome.ACCEPTED, targetId, next);
    }

    private Result replay(UUID operationId, String request) {
        var receipt = receipts.get(Objects.requireNonNull(operationId));
        if (receipt == null) return null;
        return new Result(this, receipt.request().equals(request) ? receipt :
                new Receipt(operationId, request, Outcome.REPLAY_MISMATCH, revision, receipt.entryId()));
    }
    private Result record(UUID operationId, String request, Outcome outcome, UUID entryId, Map<UUID, Entry> next) {
        long nextRevision = outcome == Outcome.ACCEPTED ? revision + 1 : revision;
        var receipt = new Receipt(operationId, request, outcome, nextRevision, entryId);
        var nextReceipts = new LinkedHashMap<>(receipts);
        nextReceipts.put(operationId, receipt);
        return new Result(new SpatialBagAggregate(owner, catalogRevision, nextRevision, next, nextReceipts), receipt);
    }
    private SpatialLayout layout() {
        var layout = new SpatialLayout(InventoryGridGeometry.COLUMNS, InventoryGridGeometry.ROWS);
        for (var entry : entries.values())
            if (!layout.add(entry.id().toString(), entry.size(), entry.position()))
                throw new IllegalStateException("Overlapping private bag");
        return layout;
    }
    private static void validate(Collection<Entry> entries) {
        var layout = new SpatialLayout(InventoryGridGeometry.COLUMNS, InventoryGridGeometry.ROWS);
        for (var entry : entries)
            if (!layout.add(entry.id().toString(), entry.size(), entry.position()))
                throw new IllegalArgumentException("Invalid private bag geometry");
    }
    private static String encode(ItemStack stack) {
        if (ItemStack.isEmpty(stack) || stack.getQuantity() <= 0) throw new IllegalArgumentException("Empty payload");
        return ItemStack.CODEC.encode(stack, new ExtraInfo()).asDocument().toJson();
    }
    private void requireCatalog(FootprintCatalog catalog) {
        if (catalog == null || catalog.revision() != catalogRevision)
            throw new IllegalStateException("Spatial catalog migration required");
    }
    private static ItemStack payload(String json) {
        return ItemStack.CODEC.decode(BsonDocument.parse(json), new ExtraInfo());
    }
}
