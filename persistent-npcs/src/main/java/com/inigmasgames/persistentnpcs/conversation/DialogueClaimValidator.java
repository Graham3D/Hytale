package com.inigmasgames.persistentnpcs.conversation;

import com.inigmasgames.persistentnpcs.task.NpcTask;
import com.inigmasgames.persistentnpcs.task.NpcTaskState;
import com.inigmasgames.persistentnpcs.perception.EnvironmentSnapshot;
import com.inigmasgames.persistentnpcs.epistemic.EpistemicClaimFirewall;
import com.inigmasgames.persistentnpcs.epistemic.EpistemicContract;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Rejects current-scene claims that have no matching deterministic task/quest state. */
public final class DialogueClaimValidator {
    private final EpistemicClaimFirewall epistemicFirewall = new EpistemicClaimFirewall();
    private static final Pattern MOVEMENT = Pattern.compile(
            "(?i)\\b(?:i(?:'m| am)|we(?:'re| are))\\s+"
                    + "(?:moving|heading|travel(?:l)?ing|crossing|walking|going|taking you|escorting)\\b");
    private static final Pattern FOLLOWING = Pattern.compile(
            "(?i)\\b(?:i(?:'m| am)|we(?:'re| are))\\s+following\\b");
    private static final Pattern WAITING = Pattern.compile(
            "(?i)\\b(?:i(?:'m| am)|we(?:'re| are))\\s+waiting\\b");
    private static final Pattern POST_OR_GUARD = Pattern.compile(
            "(?i)\\b(?:from|at) my post\\b|\\b(?:i(?:'m| am))\\s+"
                    + "(?:standing guard|guarding|watching you from)\\b");
    private static final Pattern QUEST_CURRENT = Pattern.compile(
            "(?i)\\b(?:quest|mission|objective|task)\\s+(?:is|'s)\\s+"
                    + "(?:underway|active|started|in progress)\\b");
    private static final Pattern UNSUPPORTED_SCENE = Pattern.compile(
            "(?i)\\b(?:the air(?:'s| is) dry|the weather is|it(?:'s| is) raining|"
                    + "a storm is|the heat is|potion(?:'s| is).{0,30}(?:lasts|active|working))\\b");
    private static final Pattern FICTION_FRAME = Pattern.compile(
            "(?i)^(?:here(?:'s| is) (?:a |the )?(?:fictional )?(?:story|tale)|"
                    + "once(?: upon a time)?|in this story|imagine|suppose|as a story)\\b");
    private static final Map<String, Pattern> ENVIRONMENT_CLAIMS = environmentClaims();
    private static final Pattern STRUCTURE_TERMS = Pattern.compile(
            "(?i)\\b(?:stone|masonry|structure|wall|column|pillar|ruin|ruins)\\b");
    private static final Pattern SUBJECTIVE_OR_HYPOTHETICAL = Pattern.compile(
            "(?i)\\b(?:i (?:like|love|want|wish|hope|prefer|feel|think)|in my opinion|"
                    + "if|unless|suppose|imagine|would|could|might)\\b");

    public DialogueClaimValidation validate(
            DialogueMode mode,
            String playerMessage,
            String dialogue,
            DialogueRequestState state) {
        return validate(mode, playerMessage, dialogue, state, null);
    }

    /** E3 final objective-claim boundary; legacy scene checks remain pre-validation only. */
    public EpistemicClaimFirewall.Result validateEpistemic(String dialogue,
            EpistemicContract contract, boolean authoritativeActionResult) {
        return epistemicFirewall.validate(dialogue, contract, authoritativeActionResult);
    }

    public EpistemicClaimFirewall.Result validateEpistemic(String dialogue,
            EpistemicContract contract, boolean authoritativeActionResult,
            boolean requireDirectAnswer) {
        return epistemicFirewall.validate(dialogue, contract, authoritativeActionResult,
                requireDirectAnswer);
    }

    public EpistemicClaimFirewall.Result validateEpistemic(String dialogue,
            EpistemicContract contract, String authoritativeActionResult,
            boolean requireDirectAnswer) {
        return epistemicFirewall.validate(dialogue, contract, authoritativeActionResult,
                requireDirectAnswer);
    }

    public DialogueClaimValidation validate(
            DialogueMode mode,
            String playerMessage,
            String dialogue,
            DialogueRequestState state,
            EnvironmentSnapshot environment) {
        String value = dialogue == null ? "" : dialogue.strip();
        boolean currentAction = actionClaim(value);
        if (mode == DialogueMode.FICTIONAL_STORY) {
            if (FICTION_FRAME.matcher(value).find()) {
                return new DialogueClaimValidation(value, currentAction, false,
                        "fiction was already explicitly framed");
            }
            return new DialogueClaimValidation("Here's a fictional story: " + value,
                    currentAction, true,
                    "unframed imaginative output was explicitly marked fictional");
        }

        String unsupported = unsupportedReason(value, state);
        String environmental = unsupportedEnvironmentReason(
                mode, playerMessage, value, environment);
        if (unsupported == null) {
            unsupported = environmental;
        }
        if (unsupported == null) {
            return new DialogueClaimValidation(value, currentAction, false,
                    SUBJECTIVE_OR_HYPOTHETICAL.matcher(value).find() && !currentAction
                            ? "SAFE_SOCIAL_SUBJECTIVE: no authoritative factual claim"
                            : "no unsupported current-scene claim detected");
        }
        String replacement = environmental != null && environment != null
                ? environment.groundedDescription()
                : asksCurrentActivity(playerMessage)
                ? state.currentActivityReply()
                : "That was only an idea, not something happening right now.";
        return new DialogueClaimValidation(replacement, currentAction, true, unsupported);
    }

    private static String unsupportedEnvironmentReason(
            DialogueMode mode,
            String playerMessage,
            String dialogue,
            EnvironmentSnapshot environment) {
        if (mode != DialogueMode.ENVIRONMENT_QUERY) {
            return null;
        }
        if (environment == null || !environment.isUsable()) {
            return ENVIRONMENT_CLAIMS.values().stream().anyMatch(pattern ->
                    pattern.matcher(dialogue).find())
                    ? "environmental claim emitted without a usable environment snapshot"
                    : null;
        }
        String lower = dialogue.toLowerCase(Locale.ROOT).replace('\u2019', '\'');
        String question = playerMessage == null ? ""
                : playerMessage.toLowerCase(Locale.ROOT);
        if (environment.supports("portal")
                && (question.contains("what do you see") || question.contains("what can you see")
                        || question.contains("what's around") || question.contains("whats around")
                        || question.contains("what is around") || question.contains("portal"))
                && !ENVIRONMENT_CLAIMS.get("portal").matcher(lower).find()) {
            return "prominent authoritative portal omitted from environment answer";
        }
        if ((environment.supports("stone") || environment.supports("ruins"))
                && (question.contains("what do you see") || question.contains("what can you see")
                        || question.contains("what's around") || question.contains("whats around")
                        || question.contains("what is around"))
                && !STRUCTURE_TERMS.matcher(lower).find()) {
            return "prominent authoritative stone structures omitted from environment answer";
        }
        for (Map.Entry<String, Pattern> claim : ENVIRONMENT_CLAIMS.entrySet()) {
            if (!claim.getValue().matcher(lower).find()) {
                continue;
            }
            boolean negated = isNegated(lower, claim.getKey());
            if (!environment.supports(claim.getKey()) && !negated) {
                return "unsupported present-environment category: " + claim.getKey();
            }
            if (environment.supports(claim.getKey()) && negated
                    && claim.getValue().matcher(question).find()) {
                return "authoritative environment contains queried category: " + claim.getKey();
            }
        }
        return null;
    }

    private static boolean isNegated(String dialogue, String category) {
        Pattern term = ENVIRONMENT_CLAIMS.get(category);
        Matcher matcher = term.matcher(dialogue);
        while (matcher.find()) {
            int start = Math.max(0, matcher.start() - 28);
            String prefix = dialogue.substring(start, matcher.start());
            if (prefix.matches("(?s).*\\b(?:no|not|isn't|isnt|aren't|arent|"
                    + "don't see|dont see|do not see|can't see|cant see|cannot see)\\s*(?:a|an|the)?\\s*$")) {
                return true;
            }
        }
        return false;
    }

    private static Map<String, Pattern> environmentClaims() {
        LinkedHashMap<String, Pattern> claims = new LinkedHashMap<>();
        claims.put("forest", Pattern.compile("(?i)\\b(?:forest|woods|woodland)\\b"));
        claims.put("water", Pattern.compile("(?i)\\b(?:river|ocean|sea|lake|stream|waterfall)\\b"));
        claims.put("town", Pattern.compile("(?i)\\b(?:town|village|city|settlement)\\b"));
        claims.put("building", Pattern.compile("(?i)\\b(?:building|house|home|cottage|hut)\\b"));
        claims.put("mountain", Pattern.compile("(?i)\\b(?:mountain|mountains|cliff|peak)\\b"));
        claims.put("portal", Pattern.compile("(?i)\\b(?:portal|gateway|teleporter)\\b"));
        claims.put("ruins", Pattern.compile("(?i)\\b(?:ruin|ruins|ruined)\\b"));
        claims.put("road", Pattern.compile("(?i)\\b(?:road|path|trail)\\b"));
        claims.put("weather", Pattern.compile("(?i)\\b(?:rain|raining|storm|snow|snowing|fog|windy)\\b"));
        claims.put("tree", Pattern.compile("(?i)\\b(?:tree|trees)\\b"));
        return Map.copyOf(claims);
    }

    private static String unsupportedReason(String dialogue, DialogueRequestState state) {
        if (UNSUPPORTED_SCENE.matcher(dialogue).find()) {
            return "unsupported current environmental/object-state claim";
        }
        if (QUEST_CURRENT.matcher(dialogue).find() && !state.hasActiveQuest()) {
            return "quest claimed current without a validated active quest";
        }
        if (FOLLOWING.matcher(dialogue).find() && !hasTask(state, "FOLLOW")) {
            return "following claimed current without an active follow task";
        }
        if (WAITING.matcher(dialogue).find() && !hasTask(state, "WAIT", "MEET")) {
            return "waiting claimed current without an active wait/meeting task";
        }
        if (POST_OR_GUARD.matcher(dialogue).find()
                && !hasTask(state, "PATROL", "DEFEND", "GUARD", "WORK_SHIFT")) {
            return "post/guard duty claimed current without a matching active task";
        }
        if (MOVEMENT.matcher(dialogue).find()
                && !hasTask(state, "FOLLOW", "GO_TO", "ESCORT", "PATROL", "FLEE",
                        "RETURN_HOME", "FETCH", "DELIVER")) {
            return "movement claimed current without a matching active task";
        }
        return null;
    }

    private static boolean actionClaim(String dialogue) {
        return MOVEMENT.matcher(dialogue).find() || FOLLOWING.matcher(dialogue).find()
                || WAITING.matcher(dialogue).find() || POST_OR_GUARD.matcher(dialogue).find()
                || QUEST_CURRENT.matcher(dialogue).find();
    }

    private static boolean hasTask(DialogueRequestState state, String... fragments) {
        return state.activeTasks().stream()
                .filter(task -> task.state() == NpcTaskState.ACTIVE
                        || task.state() == NpcTaskState.TRAVELING
                        || task.state() == NpcTaskState.WAITING)
                .map(NpcTask::type).map(value -> value.toUpperCase(Locale.ROOT))
                .anyMatch(type -> java.util.Arrays.stream(fragments).anyMatch(type::contains));
    }

    private static boolean asksCurrentActivity(String message) {
        String text = message == null ? "" : message.toLowerCase(Locale.ROOT);
        return text.contains("what are you doing") || text.contains("what are we doing")
                || text.contains("where are you going") || text.contains("are you following")
                || text.contains("are you waiting");
    }
}
