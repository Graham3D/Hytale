package com.inigmasgames.persistentnpcs.task;

import com.inigmasgames.persistentnpcs.json.JsonFiles;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class NpcTaskStore {
    private final Path path;
    private final List<NpcTask> tasks = new ArrayList<>();

    public NpcTaskStore(Path dataDirectory) {
        path = dataDirectory.resolve("persistence/tasks.json");
    }

    public synchronized void load() {
        tasks.clear();
        if (Files.exists(path)) {
        NpcTask[] loaded = JsonFiles.read(path, NpcTask[].class);
        if (loaded != null) {
                Arrays.stream(loaded).map(NpcTask::normalized).forEach(tasks::add);
            }
        } else {
            save();
        }
    }

    public synchronized NpcTask put(NpcTask task) {
        NpcTask normalized = task.normalized();
        tasks.removeIf(existing -> existing.taskId().equals(normalized.taskId()));
        tasks.add(normalized);
        save();
        return normalized;
    }

    public synchronized List<NpcTask> activeFor(UUID npcId) {
        return tasks.stream().filter(task -> task.npcId().equals(npcId) && !task.terminal()
                        && task.state() != NpcTaskState.SUSPENDED)
                .toList();
    }

    public synchronized List<NpcTask> all() {
        return List.copyOf(tasks);
    }

    public synchronized long activeCount() {
        return tasks.stream().filter(task -> !task.terminal()
                && task.state() != NpcTaskState.SUSPENDED).count();
    }

    public synchronized int suspendMovementTasks(
            UUID npcId, UUID guideTaskId, String reason) {
        int suspended = 0;
        for (int i = 0; i < tasks.size(); i++) {
            NpcTask current = tasks.get(i);
            if (!current.npcId().equals(npcId) || current.terminal()
                    || current.state() == NpcTaskState.SUSPENDED
                    || !isLocomotionType(current.type())) continue;
            java.util.LinkedHashMap<String, String> data =
                    new java.util.LinkedHashMap<>(current.data());
            data.put("suspendedByGuideTask", guideTaskId.toString());
            data.put("stateBeforeGuide", current.state().name());
            tasks.set(i, current.withData(data).withState(NpcTaskState.SUSPENDED, reason));
            suspended++;
        }
        if (suspended > 0) save();
        return suspended;
    }

    public synchronized int resumeAfterGuide(UUID npcId, UUID guideTaskId) {
        int resumed = 0;
        for (int i = 0; i < tasks.size(); i++) {
            NpcTask current = tasks.get(i);
            if (!current.npcId().equals(npcId)
                    || current.state() != NpcTaskState.SUSPENDED
                    || !guideTaskId.toString().equals(
                            current.data().get("suspendedByGuideTask"))) continue;
            NpcTaskState prior;
            try {
                prior = NpcTaskState.valueOf(current.data().getOrDefault(
                        "stateBeforeGuide", "ACTIVE"));
            } catch (RuntimeException ignored) {
                prior = NpcTaskState.ACTIVE;
            }
            java.util.LinkedHashMap<String, String> data =
                    new java.util.LinkedHashMap<>(current.data());
            data.remove("suspendedByGuideTask");
            data.remove("stateBeforeGuide");
            tasks.set(i, current.withData(data).withState(prior,
                    "Resumed after guiding the player."));
            resumed++;
        }
        if (resumed > 0) save();
        return resumed;
    }

    public synchronized void cancelType(UUID npcId, String type, String reason) {
        boolean changed = false;
        for (int i = 0; i < tasks.size(); i++) {
            NpcTask current = tasks.get(i);
            if (current.npcId().equals(npcId) && !current.terminal()
                    && current.type().equalsIgnoreCase(type)) {
                tasks.set(i, current.withState(NpcTaskState.CANCELLED, reason));
                changed = true;
            }
        }
        if (changed) save();
    }

    /** Cancels only R016-era follow tasks that cannot be traced to an explicit action. */
    public synchronized int cancelLegacyFollowTasks(UUID npcId, String reason) {
        int cancelled = 0;
        for (int i = 0; i < tasks.size(); i++) {
            NpcTask current = tasks.get(i);
            if (current.npcId().equals(npcId) && !current.terminal()
                    && current.type().equalsIgnoreCase("FOLLOW_PLAYER")
                    && !"FOLLOWING_PLAYER".equalsIgnoreCase(
                            current.data().getOrDefault("movementState", ""))) {
                tasks.set(i, current.withState(NpcTaskState.CANCELLED, reason));
                cancelled++;
            }
        }
        if (cancelled > 0) save();
        return cancelled;
    }

    /** Enforces one locomotion owner before beginning a new movement command. */
    public synchronized int cancelMovementTasks(UUID npcId, String reason) {
        int cancelled = 0;
        for (int i = 0; i < tasks.size(); i++) {
            NpcTask current = tasks.get(i);
            if (current.npcId().equals(npcId) && !current.terminal()
                    && isLocomotionType(current.type())) {
                tasks.set(i, current.withState(NpcTaskState.CANCELLED, reason));
                cancelled++;
            }
        }
        if (cancelled > 0) save();
        return cancelled;
    }

    private static boolean isLocomotionType(String type) {
        return switch (type == null ? "" : type.toUpperCase(Locale.ROOT)) {
            case "FOLLOW_PLAYER", "GO_TO", "PATROL", "WANDER", "FLEE", "WAIT",
                    "WAIT_UNTIL", "SCHEDULE_MEETING",
                    "ESCORT", "SEARCH_WITH_PLAYER", "GO_TO_LOCATION",
                    "FETCH_ITEM", "FETCH_PERSON", "DELIVER_ITEM",
                    "DELIVER_MESSAGE", "WORK_SHIFT", "RETURN_HOME",
                    "BRING_ITEM", "CRAFT_FOR_PLAYER", "GUIDE_PLAYER_TO_NPC" -> true;
            default -> false;
        };
    }

    public Path path() {
        return path;
    }

    private void save() {
        JsonFiles.writeAtomic(path, tasks);
    }
}
