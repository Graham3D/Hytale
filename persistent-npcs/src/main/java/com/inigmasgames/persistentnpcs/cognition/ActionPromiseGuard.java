package com.inigmasgames.persistentnpcs.cognition;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/** Prevents concrete promises from escaping without the same decision's matching action. */
public final class ActionPromiseGuard {
    private static final Pattern COMMITMENT = Pattern.compile(
            "(?i)\\b(?:i(?:'ll| will| am going to)|let me|follow me|come with me)\\b");

    private ActionPromiseGuard() { }

    public static Optional<String> violation(String spokenText,
            List<NpcDecisionAction> actions) {
        String text = spokenText == null ? "" : spokenText.toLowerCase(Locale.ROOT);
        if (!COMMITMENT.matcher(text).find()) return Optional.empty();
        Set<String> selected = actions == null ? Set.of() : actions.stream()
                .map(NpcDecisionAction::actionId).collect(java.util.stream.Collectors.toSet());
        for (Promise promise : Promise.values()) {
            if (promise.matches(text) && selected.stream().noneMatch(promise.actions::contains)) {
                return Optional.of("concrete spoken promise has no matching action: "
                        + promise.name());
            }
        }
        return Optional.empty();
    }

    private enum Promise {
        FOLLOW(List.of("follow", "come with"), Set.of("FOLLOW_PLAYER")),
        GUIDE(List.of("lead", "guide", "show you", "take you"),
                Set.of("GUIDE_PLAYER_TO_NPC", "CREATE_SHARED_PLAN")),
        MOVE(List.of("go to", "walk to", "head to", "move there", "travel to"),
                Set.of("GO_TO", "PATROL", "WANDER", "FLEE")),
        WAIT(List.of("wait", "stay here"), Set.of("WAIT", "STOP_FOLLOWING")),
        ITEM(List.of("bring", "fetch", "pick up", "give", "take", "drop"),
                Set.of("BRING_ITEM", "PICK_UP_ITEM", "GIVE_ITEM", "TAKE_ITEM",
                        "DROP_ITEM")),
        CRAFT(List.of("craft", "forge", "cook", "process", "repair", "make you"),
                Set.of("CRAFT_ITEM", "COOK_ITEM", "PROCESS_ITEM")),
        SCHEDULE(List.of("schedule", "meet you"),
                Set.of("SCHEDULE_MEETING", "SCHEDULE_TASK", "CREATE_SHARED_PLAN")),
        COMBAT(List.of("attack", "fight", "defend", "guard you"),
                Set.of("ATTACK", "DEFEND", "CEASE_COMBAT")),
        SEARCH(List.of("search", "look for", "find them", "deliver"),
                Set.of("SEARCH_WITH_PLAYER", "FETCH_PERSON", "DELIVER_ITEM",
                        "DELIVER_MESSAGE", "BRING_ITEM"));

        private final List<String> phrases;
        private final Set<String> actions;

        Promise(List<String> phrases, Set<String> actions) {
            this.phrases = phrases;
            this.actions = actions;
        }

        private boolean matches(String text) {
            return phrases.stream().anyMatch(text::contains);
        }
    }
}
