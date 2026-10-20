package com.inigmasgames.hytalerpg.ui.inventory;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.bson.BsonDocument;
import java.util.Objects;
import java.util.UUID;

/** Candidate same-player save participant. Registration does not attach or migrate any player. */
public final class SpatialBagComponent implements Component<EntityStore> {
    public enum OwnershipMode { NATIVE, QA_PROOF, MIGRATION_PROOF, SPATIAL }
    public static final BuilderCodec<SpatialBagComponent> CODEC = BuilderCodec.builder(
            SpatialBagComponent.class, SpatialBagComponent::new)
            .append(new KeyedCodec<>("State", Codec.BSON_DOCUMENT),
                    (component, document) -> component.state = SpatialBagAggregate.fromBson(document, FootprintCatalog.loadDefault()),
                    component -> component.state == null ? null : component.state.toBson())
            .add()
            .append(new KeyedCodec<>("OwnershipMode", Codec.STRING),
                    (component, value) -> component.mode = OwnershipMode.valueOf(value),
                    component -> component.mode.name())
            .add()
            .append(new KeyedCodec<>("Rings", Codec.BSON_DOCUMENT),
                    (component, document) -> component.rings = RpgRingEquipment.fromBson(document),
                    component -> component.rings.toBson())
            .add().build();
    private static ComponentType<EntityStore, SpatialBagComponent> type;
    private SpatialBagAggregate state;
    private OwnershipMode mode = OwnershipMode.NATIVE;
    private RpgRingEquipment rings = new RpgRingEquipment(null, null);

    public SpatialBagComponent() { }
    public SpatialBagComponent(UUID owner, FootprintCatalog catalog) {
        state = new SpatialBagAggregate(Objects.requireNonNull(owner), catalog.revision());
    }
    private SpatialBagComponent(SpatialBagAggregate state, OwnershipMode mode, RpgRingEquipment rings) {
        this.state = state; this.mode = mode; this.rings = rings;
    }
    public static void bind(ComponentType<EntityStore, SpatialBagComponent> value) { type = value; }
    public static ComponentType<EntityStore, SpatialBagComponent> getComponentType() { return type; }
    public SpatialBagAggregate state(UUID owner) {
        if (state == null || !state.owner().equals(owner)) throw new IllegalStateException("Spatial owner mismatch");
        return state;
    }
    public OwnershipMode mode(UUID owner) { state(owner); return mode; }
    public RpgRingEquipment rings(UUID owner) { state(owner); return rings; }
    /** The bag exchange and ring slot change publish together in this one saved component. */
    public synchronized SpatialBagAggregate.Result equipRing(UUID owner, String side, UUID sourceId,
                                                               long expectedRevision, FootprintCatalog catalog) {
        if (mode == OwnershipMode.NATIVE) throw new IllegalStateException("Spatial ownership required");
        var before = state(owner);
        if (before.revision() != expectedRevision) throw new IllegalStateException("Stale bag revision");
        var source = before.entry(sourceId).orElseThrow(() -> new IllegalArgumentException("Ring is no longer in bag"));
        if (!RpgRingEquipment.eligible(source.payload())) throw new IllegalArgumentException("Only a ring fits this slot");
        var displaced = rings.get(side);
        var offered = displaced == null ? java.util.List.<SpatialBagAggregate.OfferedItem>of()
                : java.util.List.of(new SpatialBagAggregate.OfferedItem(displaced.entryId(), displaced.payload()));
        var result = before.exchange(UUID.randomUUID(), expectedRevision, sourceId, source.payloadJson(),
                offered, catalog);
        if (result.accepted()) {
            state = result.bag();
            rings = rings.with(side, RpgRingEquipment.Slot.of(sourceId, source.payload()));
        }
        return result;
    }
    public synchronized SpatialBagAggregate.Result unequipRing(UUID owner, String side, FootprintCatalog catalog) {
        if (mode == OwnershipMode.NATIVE) throw new IllegalStateException("Spatial ownership required");
        var before = state(owner);
        var slot = rings.get(side);
        if (slot == null) throw new IllegalArgumentException("Ring slot is empty");
        var result = before.offerAll(UUID.randomUUID(), before.revision(),
                java.util.List.of(new SpatialBagAggregate.OfferedItem(slot.entryId(), slot.payload())), catalog);
        if (result.accepted()) {
            state = result.bag();
            rings = rings.with(side, null);
        }
        return result;
    }
    /** Copied-save proof only. The caller must enforce the diagnostic marker before invocation. */
    public synchronized void activateQaProof(UUID owner) {
        state(owner);
        if (mode != OwnershipMode.NATIVE || !state.entries().isEmpty())
            throw new IllegalStateException("QA isolation requires a new empty bag");
        mode = OwnershipMode.QA_PROOF;
    }
    /** Migration may call this only after every native route and reverse export is qualified. */
    public synchronized void activateSpatialAfterMigration(UUID owner, SpatialBagAggregate expected) {
        if (state != expected || !state.owner().equals(owner) || mode != OwnershipMode.NATIVE)
            throw new IllegalStateException("Invalid spatial cutover");
        SpatialRoutePolicy.requireReleaseReady();
        mode = OwnershipMode.SPATIAL;
    }
    /** Publish only a candidate planned against this exact snapshot. Rejected intents may add
     * a durable replay receipt without advancing the placement revision. */
    public synchronized void publish(UUID owner, SpatialBagAggregate expected, SpatialBagAggregate candidate) {
        if (state == null || state != expected || !state.owner().equals(owner)
                || candidate == null || !candidate.owner().equals(owner)
                || candidate.catalogRevision() != state.catalogRevision()
                || candidate.revision() < state.revision()
                || candidate.revision() > state.revision() + 1)
            throw new IllegalStateException("Stale or invalid spatial candidate");
        state = candidate;
    }
    /** Disposable-save-only cutover; caller must enforce the copied-save marker and durable journal. */
    public synchronized void publishCopiedMigration(UUID owner,SpatialBagAggregate expected,
                                                     SpatialBagAggregate candidate,OwnershipMode target) {
        if (target == OwnershipMode.QA_PROOF && (rings.left() != null || rings.right() != null))
            throw new IllegalStateException("Equipped rings must be returned before reverse export");
        if(!((mode==OwnershipMode.QA_PROOF&&target==OwnershipMode.MIGRATION_PROOF)
                ||(mode==OwnershipMode.MIGRATION_PROOF&&target==OwnershipMode.QA_PROOF&&candidate.entries().isEmpty())))
            throw new IllegalStateException("Invalid copied-save migration mode transition");
        publish(owner,expected,candidate);
        mode=target;
    }
    public BsonDocument snapshot() { return state == null ? null : state.toBson(); }
    @Override public SpatialBagComponent clone() { return new SpatialBagComponent(state, mode, rings); }
}
