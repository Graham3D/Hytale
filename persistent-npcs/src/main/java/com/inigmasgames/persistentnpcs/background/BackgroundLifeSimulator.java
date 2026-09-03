package com.inigmasgames.persistentnpcs.background;

import com.inigmasgames.persistentnpcs.autonomy.SimulationTier;
import com.inigmasgames.persistentnpcs.memory.MemoryRecord;
import com.inigmasgames.persistentnpcs.memory.MemoryStore;
import com.inigmasgames.persistentnpcs.memory.MemoryType;
import com.inigmasgames.persistentnpcs.profile.NpcProfile;
import com.inigmasgames.persistentnpcs.profile.NpcScheduleEntry;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;

/** Advances unloaded NPCs as logical history and loaded NPCs only as observed status. */
public final class BackgroundLifeSimulator {
    private static final int MAX_HISTORY = 64;
    private final BackgroundLifeStore store;
    private final MemoryStore memories;
    private final Consumer<String> diagnostics;

    public BackgroundLifeSimulator(
            BackgroundLifeStore store, MemoryStore memories, Consumer<String> diagnostics) {
        this.store = store;
        this.memories = memories;
        this.diagnostics = diagnostics == null ? ignored -> { } : diagnostics;
    }

    public BackgroundLifeState markLoaded(
            NpcProfile profile, UUID worldId, String logicalLocation, Instant now) {
        BackgroundLifeState prior = store.getOrCreate(
                profile.id(), worldId, profile.home()).normalized();
        if (prior.tier() == SimulationTier.ACTIVE
                && elapsed(prior.lastAdvancedAt(), now).compareTo(Duration.ofSeconds(30)) < 0
                && java.util.Objects.equals(prior.worldId(), worldId)) {
            return prior;
        }
        BackgroundLifeState updated = new BackgroundLifeState(profile.id(), worldId,
                SimulationTier.ACTIVE, prior.activity(),
                logicalLocation == null || logicalLocation.isBlank()
                        ? prior.logicalLocation() : logicalLocation,
                prior.destination(), prior.goal(), prior.activityStartedAt(),
                prior.activityEndsAt(), prior.inProgressOperation(), now,
                prior.nextDecisionAt(), prior.history());
        return store.put(updated);
    }

    public BackgroundLifeState advanceUnloaded(
            NpcProfile profile, UUID worldId, Instant gameNow) {
        BackgroundLifeState state = store.getOrCreate(
                profile.id(), worldId, profile.home()).normalized();
        SimulationTier tier = elapsed(state.lastAdvancedAt(), gameNow).compareTo(
                Duration.ofHours(6)) >= 0 ? SimulationTier.DORMANT : SimulationTier.BACKGROUND;
        state = withTier(state, tier, gameNow);
        if (state.inProgressOperation() != null) return state;
        if (gameNow.isBefore(state.nextDecisionAt())) return state;
        if (state.activity() != BackgroundActivityType.IDLE
                && gameNow.isBefore(state.activityEndsAt())) return state;
        if (state.activity() != BackgroundActivityType.IDLE) {
            state = complete(state, gameNow);
        }
        DesiredActivity desired = desired(profile, gameNow);
        if (!samePlace(state.logicalLocation(), desired.location())) {
            return begin(state, BackgroundActivityType.TRAVEL, desired.location(),
                    "travel to " + desired.location() + " for " + desired.type(),
                    gameNow, gameNow.plus(Duration.ofMinutes(20)));
        }
        return begin(state, desired.type(), desired.location(), desired.goal(), gameNow,
                gameNow.plus(duration(desired.type())));
    }

    public String debug(UUID npcId) {
        BackgroundLifeState state = store.get(npcId);
        if (state == null) return "BACKGROUND LIFE: no state";
        return """
                BACKGROUND LIFE DEBUG
                tier=%s activity=%s location=%s destination=%s
                goal=%s operation=%s started=%s ends=%s nextDecision=%s
                history=%s
                """.formatted(state.tier(), state.activity(), state.logicalLocation(),
                state.destination(), state.goal(), state.inProgressOperation(),
                state.activityStartedAt(), state.activityEndsAt(), state.nextDecisionAt(),
                state.history().stream().map(BackgroundLifeEvent::summary).toList()).strip();
    }

    private BackgroundLifeState complete(BackgroundLifeState state, Instant now) {
        String location = state.activity() == BackgroundActivityType.TRAVEL
                ? state.destination() : state.logicalLocation();
        String summary = switch (state.activity()) {
            case TRAVEL -> "Arrived at " + location;
            case WORK -> "Completed part of an ordinary work period at " + location;
            case REST -> "Rested at " + location;
            case SOCIALIZE -> "Spent a short period socializing at " + location;
            case IDLE -> "Remained idle at " + location;
        };
        BackgroundLifeEvent event = new BackgroundLifeEvent(UUID.randomUUID(),
                state.activity(), location, summary, state.activityStartedAt(), now,
                "BACKGROUND_LOGICAL_SIMULATION", false);
        List<BackgroundLifeEvent> history = new ArrayList<>(state.history());
        history.add(event);
        if (history.size() > MAX_HISTORY) history = history.subList(
                history.size() - MAX_HISTORY, history.size());
        memories.append(new MemoryRecord(UUID.randomUUID(), state.npcId(), null, now,
                MemoryType.EPISODIC, 0.38, summary, 0.9,
                event.source(), List.of(), location,
                "While away from the player, I " + summary.toLowerCase(Locale.ROOT) + "."));
        diagnostics.accept("BACKGROUND_ACTIVITY_COMPLETE npc=" + state.npcId()
                + " activity=" + state.activity() + " result=" + summary);
        return store.put(new BackgroundLifeState(state.npcId(), state.worldId(), state.tier(),
                BackgroundActivityType.IDLE, location, "", state.goal(), now, now,
                null, now, now, List.copyOf(history)));
    }

    private BackgroundLifeState begin(BackgroundLifeState state, BackgroundActivityType type,
            String destination, String goal, Instant now, Instant ends) {
        UUID operation = UUID.randomUUID();
        BackgroundLifeState updated = new BackgroundLifeState(state.npcId(), state.worldId(),
                state.tier(), type, state.logicalLocation(), destination, goal, now, ends,
                operation, now, ends, state.history());
        diagnostics.accept("BACKGROUND_ACTIVITY_BEGIN npc=" + state.npcId()
                + " activity=" + type + " destination=" + destination
                + " operation=" + operation);
        // Logical activities are deterministic and complete on a later advancement. The UUID
        // is retained as provenance, but no asynchronous model operation is left running.
        return store.put(new BackgroundLifeState(updated.npcId(), updated.worldId(),
                updated.tier(), updated.activity(), updated.logicalLocation(),
                updated.destination(), updated.goal(), updated.activityStartedAt(),
                updated.activityEndsAt(), null, updated.lastAdvancedAt(),
                updated.nextDecisionAt(), updated.history()));
    }

    private static DesiredActivity desired(NpcProfile profile, Instant now) {
        int hour = now.atZone(ZoneOffset.UTC).getHour();
        for (NpcScheduleEntry entry : profile.defaultSchedule()) {
            if (containsHour(entry, hour)) {
                BackgroundActivityType type = switch (entry.taskType()) {
                    case "WORK_SHIFT", "PATROL" -> BackgroundActivityType.WORK;
                    case "RETURN_HOME", "SLEEP", "REST" -> BackgroundActivityType.REST;
                    default -> BackgroundActivityType.IDLE;
                };
                String location = resolveLocation(profile, entry.location());
                return new DesiredActivity(type, location,
                        "follow authored schedule: " + entry.taskType());
            }
        }
        if (hour >= 20 || hour < 7) {
            return new DesiredActivity(BackgroundActivityType.REST, profile.home(),
                    "rest between scheduled activities");
        }
        // Deterministic infrequent social time; it creates no named encounter without a real
        // second NPC and therefore cannot fabricate a conversation.
        if (hour == 18 && profile.sociability() != null && profile.sociability() >= 0.55) {
            return new DesiredActivity(BackgroundActivityType.SOCIALIZE, profile.home(),
                    "remain socially available near home");
        }
        return new DesiredActivity(BackgroundActivityType.IDLE, profile.home(),
                "remain available and attend to ordinary needs");
    }

    private static boolean containsHour(NpcScheduleEntry entry, int hour) {
        return entry.startHour() <= entry.endHour()
                ? hour >= entry.startHour() && hour < entry.endHour()
                : hour >= entry.startHour() || hour < entry.endHour();
    }

    private static String resolveLocation(NpcProfile profile, String authored) {
        if (authored == null || authored.isBlank() || authored.equalsIgnoreCase("home")) {
            return profile.home();
        }
        if (authored.equalsIgnoreCase("workplace")) return profile.workplace();
        return authored;
    }

    private static Duration duration(BackgroundActivityType type) {
        return switch (type) {
            case WORK -> Duration.ofHours(2);
            case REST -> Duration.ofHours(2);
            case SOCIALIZE -> Duration.ofMinutes(30);
            case IDLE -> Duration.ofMinutes(45);
            case TRAVEL -> Duration.ofMinutes(20);
        };
    }

    private static boolean samePlace(String left, String right) {
        return left != null && right != null && left.strip().equalsIgnoreCase(right.strip());
    }

    private static Duration elapsed(Instant from, Instant to) {
        return from == null || from.equals(Instant.EPOCH) ? Duration.ofDays(1)
                : Duration.between(from, to).abs();
    }

    private static BackgroundLifeState withTier(
            BackgroundLifeState state, SimulationTier tier, Instant now) {
        return new BackgroundLifeState(state.npcId(), state.worldId(), tier, state.activity(),
                state.logicalLocation(), state.destination(), state.goal(),
                state.activityStartedAt(), state.activityEndsAt(), state.inProgressOperation(),
                now, state.nextDecisionAt(), state.history());
    }

    private record DesiredActivity(
            BackgroundActivityType type, String location, String goal) { }
}
