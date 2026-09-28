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
            .add().build();
    private static ComponentType<EntityStore, SpatialBagComponent> type;
    private SpatialBagAggregate state;
    private OwnershipMode mode = OwnershipMode.NATIVE;

    public SpatialBagComponent() { }
    public SpatialBagComponent(UUID owner, FootprintCatalog catalog) {
        state = new SpatialBagAggregate(Objects.requireNonNull(owner), catalog.revision());
    }
    private SpatialBagComponent(SpatialBagAggregate state, OwnershipMode mode) { this.state = state; this.mode = mode; }
    public static void bind(ComponentType<EntityStore, SpatialBagComponent> value) { type = value; }
    public static ComponentType<EntityStore, SpatialBagComponent> getComponentType() { return type; }
    public SpatialBagAggregate state(UUID owner) {
        if (state == null || !state.owner().equals(owner)) throw new IllegalStateException("Spatial owner mismatch");
        return state;
    }
    public OwnershipMode mode(UUID owner) { state(owner); return mode; }
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
        if(!((mode==OwnershipMode.QA_PROOF&&target==OwnershipMode.MIGRATION_PROOF)
                ||(mode==OwnershipMode.MIGRATION_PROOF&&target==OwnershipMode.QA_PROOF&&candidate.entries().isEmpty())))
            throw new IllegalStateException("Invalid copied-save migration mode transition");
        publish(owner,expected,candidate);
        mode=target;
    }
    public BsonDocument snapshot() { return state == null ? null : state.toBson(); }
    @Override public SpatialBagComponent clone() { return new SpatialBagComponent(state, mode); }
}
