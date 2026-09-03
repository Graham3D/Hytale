package com.inigmasgames.persistentnpcs.monster;

import java.time.Duration;
import java.time.Instant;

/** Validates high-level intent while leaving navigation and combat execution native. */
public final class MonsterReasoningAdapter {
    private final Duration cooldown;

    public MonsterReasoningAdapter(Duration cooldown) {
        this.cooldown = cooldown == null ? Duration.ofSeconds(30) : cooldown;
    }

    public boolean shouldReason(
            ImmersiveEntityAgent untrustedAgent, MonsterReasoningContext context, Instant now) {
        ImmersiveEntityAgent agent = untrustedAgent.normalized();
        return context != null && context.trigger() != null
                && (agent.lastReasonedAt() == null
                        || !now.isBefore(agent.lastReasonedAt().plus(cooldown)));
    }

    public MonsterIntentResult apply(
            ImmersiveEntityAgent untrustedAgent,
            MonsterReasoningContext context,
            HighLevelIntent proposedIntent,
            Instant now) {
        ImmersiveEntityAgent agent = untrustedAgent.normalized();
        if (!shouldReason(agent, context, now)) {
            return new MonsterIntentResult(false, agent, true,
                    "Reasoning cooldown active or trigger missing");
        }
        HighLevelIntent intent = proposedIntent == null
                ? HighLevelIntent.CONTINUE_NATIVE_BEHAVIOR : proposedIntent;
        boolean valid = switch (intent) {
            case SURRENDER -> context.healthRatio() <= 0.25;
            case TEMPORARY_TRUCE, TALK -> context.playerAttemptedConversation()
                    || context.playerSparedNpc();
            case FLEE -> context.healthRatio() <= 0.35
                    || context.nearbyEnemies() > context.nearbyAllies() + 1;
            case RESUME_HOSTILITY -> agent.nativeCombatSuspended()
                    && context.playerAttackedDuringTruce();
            case OFFER_QUEST -> context.validatedQuestOpportunity();
            case FOLLOW -> context.playerSparedNpc();
            case REQUEST_HELP -> context.resolvableCampExists()
                    || context.validatedQuestOpportunity();
            case THREATEN, CONTINUE_NATIVE_BEHAVIOR -> true;
        };
        if (!valid) {
            return new MonsterIntentResult(false,
                    agent.withIntent(HighLevelIntent.CONTINUE_NATIVE_BEHAVIOR,
                            agent.nativeHostile(), false, now), true,
                    "Proposed intent failed authoritative transition requirements");
        }
        boolean suspend = switch (intent) {
            case SURRENDER, TEMPORARY_TRUCE, TALK, FOLLOW, REQUEST_HELP, OFFER_QUEST -> true;
            default -> false;
        };
        boolean hostile = intent == HighLevelIntent.RESUME_HOSTILITY
                || intent == HighLevelIntent.THREATEN
                || (agent.nativeHostile() && !suspend && intent != HighLevelIntent.FLEE);
        ImmersiveEntityAgent updated = agent.withIntent(intent, hostile, suspend, now);
        if (context.playerSparedNpc() || context.validatedQuestOpportunity()
                || intent == HighLevelIntent.OFFER_QUEST) {
            updated = updated.promote(context.validatedQuestOpportunity()
                    ? "Quest involvement" : "Player spared and engaged monster");
        }
        return new MonsterIntentResult(true, updated,
                intent == HighLevelIntent.CONTINUE_NATIVE_BEHAVIOR
                        || intent == HighLevelIntent.THREATEN
                        || intent == HighLevelIntent.RESUME_HOSTILITY,
                "Validated high-level overlay; native behavior tree remains authoritative");
    }
}
