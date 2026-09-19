package com.inigmasgames.persistentnpcs.perception;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.inigmasgames.persistentnpcs.conversation.ConversationSession;
import com.inigmasgames.persistentnpcs.hytale.NpcRuntimeRegistry;
import com.inigmasgames.persistentnpcs.profile.NpcProfile;
import com.inigmasgames.persistentnpcs.profile.NpcProfileRegistry;
import com.inigmasgames.persistentnpcs.relationship.RelationshipStore;
import java.util.Comparator;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.joml.Vector3d;

/** Relationship-gated, stable-entity lookup bounded to 500 blocks around the speaker. */
public final class KnownNpcLocatorService {
    public static final double MAX_RANGE = 500.0;
    private final NpcProfileRegistry profiles;
    private final RelationshipStore relationships;
    private final NpcRuntimeRegistry runtimes;

    public KnownNpcLocatorService(NpcProfileRegistry profiles,
            RelationshipStore relationships, NpcRuntimeRegistry runtimes) {
        this.profiles = profiles;
        this.relationships = relationships;
        this.runtimes = runtimes;
    }

    public CompletableFuture<KnownNpcLocatorResult> locate(
            NpcProfile speaker, ConversationSession session, String message) {
        TargetRequest request = resolveRequest(speaker, session, message);
        if (request == null) return CompletableFuture.completedFuture(null);
        NpcProfile target = request.target();
        if (!relationships.knows(speaker.id(), target.id())) {
            return CompletableFuture.completedFuture(new KnownNpcLocatorResult(
                    KnownNpcLocationStatus.UNKNOWN_RELATIONSHIP, target.id(), target.name(),
                    "unknown distance", "unknown direction", "location unknown", false,
                    request.directGuideRequest()));
        }
        NpcRuntimeRegistry.RuntimeNpc source = runtimes.forProfile(speaker.id()).orElse(null);
        NpcRuntimeRegistry.RuntimeNpc destination = runtimes.forProfile(target.id()).orElse(null);
        if (source == null || destination == null || source.worldId() == null
                || !source.worldId().equals(destination.worldId())) {
            return CompletableFuture.completedFuture(notLoaded(target, request));
        }
        World world = Universe.get().getWorld(source.worldId());
        if (world == null || !world.isAlive()) {
            return CompletableFuture.completedFuture(notLoaded(target, request));
        }
        CompletableFuture<KnownNpcLocatorResult> future = new CompletableFuture<>();
        world.execute(() -> {
            try {
                Store<EntityStore> store = world.getEntityStore().getStore();
                Ref<EntityStore> sourceRef = world.getEntityRef(source.entityId());
                Ref<EntityStore> targetRef = world.getEntityRef(destination.entityId());
                TransformComponent sourceTransform = transform(sourceRef, store);
                TransformComponent targetTransform = transform(targetRef, store);
                if (sourceTransform == null || targetTransform == null) {
                    future.complete(notLoaded(target, request));
                    return;
                }
                Vector3d delta = new Vector3d(targetTransform.getPosition())
                        .sub(sourceTransform.getPosition());
                double distance = delta.length();
                if (distance > MAX_RANGE) {
                    future.complete(new KnownNpcLocatorResult(
                            KnownNpcLocationStatus.NOT_FOUND, target.id(), target.name(),
                            distanceBand(distance), direction(delta), "outside the nearby area",
                            false, request.directGuideRequest()));
                    return;
                }
                future.complete(new KnownNpcLocatorResult(KnownNpcLocationStatus.FOUND,
                        target.id(), target.name(), distanceBand(distance), direction(delta),
                        "within the currently loaded area", true,
                        request.directGuideRequest()));
            } catch (Throwable failure) {
                future.complete(notLoaded(target, request));
            }
        });
        return future;
    }

    public TargetRequest resolveRequest(
            NpcProfile speaker, ConversationSession session, String message) {
        String text = normalize(message);
        ConversationSession.PendingGuideOffer pending = session.pendingGuideOffer();
        if (pending != null && isRejection(text)) {
            session.clearPendingGuideOffer();
            return null;
        }
        if (pending != null && isAcceptance(text)) {
            return profiles.byId(pending.targetId())
                    .map(target -> new TargetRequest(target, true, true)).orElse(null);
        }
        boolean locationQuestion = contains(text, "where is", "where's", "find ",
                "locate ", "do you know where", "have you seen", "take me to",
                "lead me to", "show me where", "bring me to");
        if (!locationQuestion) return null;
        boolean directGuide = contains(text, "take me to", "lead me to", "bring me to",
                "show me where");
        return profiles.profiles().stream()
                .filter(target -> !target.id().equals(speaker.id()))
                .filter(target -> containsWholeName(text, target.name()))
                .sorted(Comparator.comparingInt((NpcProfile target) -> target.name().length())
                        .reversed())
                .map(target -> new TargetRequest(target, directGuide, false))
                .findFirst().orElse(null);
    }

    public static String distanceBand(double distance) {
        if (distance <= 10) return "nearby";
        if (distance <= 50) return "a short walk away";
        if (distance <= 150) return "some distance away";
        if (distance <= 300) return "far away";
        return "near the edge of the area";
    }

    public static String direction(Vector3d delta) {
        if (delta == null || delta.x * delta.x + delta.z * delta.z < 1.0) return "same place";
        double degrees = Math.toDegrees(Math.atan2(delta.x, delta.z));
        if (degrees < 0) degrees += 360;
        String[] directions = {"north", "northeast", "east", "southeast",
                "south", "southwest", "west", "northwest"};
        return directions[(int) Math.round(degrees / 45.0) % directions.length];
    }

    private static TransformComponent transform(
            Ref<EntityStore> ref, Store<EntityStore> store) {
        return ref == null || !ref.isValid() ? null
                : store.getComponent(ref, TransformComponent.getComponentType());
    }

    private static KnownNpcLocatorResult notLoaded(
            NpcProfile target, TargetRequest request) {
        return new KnownNpcLocatorResult(KnownNpcLocationStatus.NOT_LOADED,
                target.id(), target.name(), "unknown distance", "unknown direction",
                "not presently loaded", false, request.directGuideRequest());
    }

    private static boolean containsWholeName(String text, String name) {
        return (" " + text + " ").contains(" " + normalize(name) + " ");
    }

    private static boolean isAcceptance(String text) {
        return text.equals("yes") || text.equals("sure") || text.equals("okay")
                || text.equals("ok") || text.equals("please") || text.equals("lead on")
                || text.equals("let's go") || text.equals("lets go")
                || contains(text, "show me", "take me", "lead me", "guide me");
    }

    private static boolean isRejection(String text) {
        return text.equals("no") || text.equals("no thanks") || text.equals("never mind")
                || text.equals("nevermind") || text.equals("not now");
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}' ]", " ").replaceAll("\\s+", " ").strip();
    }

    private static boolean contains(String value, String... needles) {
        for (String needle : needles) if (value.contains(needle)) return true;
        return false;
    }

    public record TargetRequest(
            NpcProfile target, boolean directGuideRequest, boolean acceptedOffer) { }
}
