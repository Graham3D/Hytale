package com.inigmasgames.persistentnpcs.conversation;

import com.inigmasgames.persistentnpcs.llm.ChatMessage;
import com.inigmasgames.persistentnpcs.llm.LlmRequest;
import com.inigmasgames.persistentnpcs.llm.LlmToolDefinition;
import com.inigmasgames.persistentnpcs.memory.MemoryRecord;
import com.inigmasgames.persistentnpcs.memory.MemoryStore;
import com.inigmasgames.persistentnpcs.profile.NpcProfile;
import com.inigmasgames.persistentnpcs.epistemic.EpistemicContract;
import com.inigmasgames.persistentnpcs.epistemic.EpistemicFeatureMode;
import com.inigmasgames.persistentnpcs.relationship.RelationshipRecord;
import com.inigmasgames.persistentnpcs.relationship.RelationshipStore;
import com.inigmasgames.persistentnpcs.task.NpcTask;
import com.inigmasgames.persistentnpcs.task.NpcTaskStore;
import com.inigmasgames.persistentnpcs.quest.DynamicQuestStore;
import com.inigmasgames.persistentnpcs.quest.QuestStatus;
import com.inigmasgames.persistentnpcs.task.NpcTaskState;
import com.inigmasgames.persistentnpcs.cognition.CognitionTurn;
import com.inigmasgames.persistentnpcs.voice.LysanderVoiceBehavior;
import com.inigmasgames.persistentnpcs.plan.SharedPlanStore;
import java.util.List;
import java.util.UUID;
import com.inigmasgames.persistentnpcs.perception.NpcPerceptionSnapshot;
import com.inigmasgames.persistentnpcs.perception.RawPerceptionSnapshot;
import com.inigmasgames.persistentnpcs.perception.SemanticPerceptionNormalizer;
import com.inigmasgames.persistentnpcs.perception.SemanticWorldModel;
import java.util.stream.Collectors;

public final class ConversationContextBuilder {
    private final java.util.concurrent.ConcurrentHashMap<UUID, StaticPrefetch> staticPrefetch =
            new java.util.concurrent.ConcurrentHashMap<>();
    private final RelationshipStore relationships;
    private final MemoryStore memories;
    private final NpcTaskStore tasks;
    private final int recentMemoryCount;
    private final DynamicQuestStore quests;
    private final SharedPlanStore sharedPlans;

    public ConversationContextBuilder(
            RelationshipStore relationships, MemoryStore memories, int recentMemoryCount) {
        this(relationships, memories, null, null, null, recentMemoryCount);
    }

    public ConversationContextBuilder(
            RelationshipStore relationships,
            MemoryStore memories,
            NpcTaskStore tasks,
            int recentMemoryCount) {
        this(relationships, memories, tasks, null, null, recentMemoryCount);
    }

    public ConversationContextBuilder(
            RelationshipStore relationships,
            MemoryStore memories,
            NpcTaskStore tasks,
            DynamicQuestStore quests,
            int recentMemoryCount) {
        this(relationships, memories, tasks, quests, null, recentMemoryCount);
    }

    public ConversationContextBuilder(
            RelationshipStore relationships,
            MemoryStore memories,
            NpcTaskStore tasks,
            DynamicQuestStore quests,
            SharedPlanStore sharedPlans,
            int recentMemoryCount) {
        this.relationships = relationships;
        this.memories = memories;
        this.tasks = tasks;
        this.quests = quests;
        this.sharedPlans = sharedPlans;
        this.recentMemoryCount = recentMemoryCount;
    }

    public LlmRequest build(
            ConversationSession session,
            NpcProfile profile,
            String playerMessage,
            MinimalWorldContext worldContext) {
        return build(session, profile, playerMessage, worldContext,
                NpcPerceptionSnapshot.unavailable(profile.id()), List.of());
    }

    public LlmRequest build(
            ConversationSession session,
            NpcProfile profile,
            String playerMessage,
            MinimalWorldContext worldContext,
            NpcPerceptionSnapshot perception,
            List<LlmToolDefinition> tools) {
        return build(session, profile, playerMessage, worldContext, perception, tools,
                ConversationGrounding.none());
    }

    public LlmRequest build(
            ConversationSession session,
            NpcProfile profile,
            String playerMessage,
            MinimalWorldContext worldContext,
            NpcPerceptionSnapshot perception,
            List<LlmToolDefinition> tools,
            ConversationGrounding grounding) {
        return build(session, profile, playerMessage, worldContext, perception, tools,
                grounding, requestState(session, profile, playerMessage));
    }

    /** Production-style compact ordinary prompt for direct provider regression tests. */
    public LlmRequest buildCompact(
            ConversationSession session,
            NpcProfile profile,
            String playerMessage,
            MinimalWorldContext worldContext,
            NpcPerceptionSnapshot perception,
            List<LlmToolDefinition> tools) {
        DialogueRequestState state = requestState(session, profile, playerMessage);
        return build(session, profile, playerMessage, worldContext, perception, tools,
                ConversationGrounding.none(), state, null, true);
    }

    public LlmRequest build(
            ConversationSession session,
            NpcProfile profile,
            String playerMessage,
            MinimalWorldContext worldContext,
            NpcPerceptionSnapshot perception,
            List<LlmToolDefinition> tools,
            ConversationGrounding grounding,
            DialogueRequestState requestState) {
        return build(session, profile, playerMessage, worldContext, perception, tools,
                grounding, requestState, null);
    }

    public LlmRequest build(
            ConversationSession session,
            NpcProfile profile,
            String playerMessage,
            MinimalWorldContext worldContext,
            NpcPerceptionSnapshot perception,
            List<LlmToolDefinition> tools,
            ConversationGrounding grounding,
            DialogueRequestState requestState,
            CognitionTurn cognition) {
        return build(session, profile, playerMessage, worldContext, perception, tools,
                grounding, requestState, cognition, false);
    }

    public LlmRequest build(
            ConversationSession session,
            NpcProfile profile,
            String playerMessage,
            MinimalWorldContext worldContext,
            NpcPerceptionSnapshot perception,
            List<LlmToolDefinition> tools,
            ConversationGrounding grounding,
            DialogueRequestState requestState,
            CognitionTurn cognition,
            boolean preferCompactOrdinaryPrompt) {
        return build(session, profile, playerMessage, worldContext, perception, tools,
                grounding, requestState, cognition, preferCompactOrdinaryPrompt,
                CognitiveContextPlan.full(requestState.mode().name()));
    }

    public LlmRequest build(
            ConversationSession session,
            NpcProfile profile,
            String playerMessage,
            MinimalWorldContext worldContext,
            NpcPerceptionSnapshot perception,
            List<LlmToolDefinition> tools,
            ConversationGrounding grounding,
            DialogueRequestState requestState,
            CognitionTurn cognition,
            boolean preferCompactOrdinaryPrompt,
            CognitiveContextPlan contextPlan) {
        CognitiveContextPlan routed = contextPlan == null
                ? CognitiveContextPlan.full(requestState.mode().name()) : contextPlan;
        StaticPrefetch prefetched = staticPrefetch.get(session.sessionId());
        if (prefetched != null && !prefetched.matches(profile, session)) {
            staticPrefetch.remove(session.sessionId(), prefetched);
            prefetched = null;
        }
        RelationshipRecord relationship = prefetched == null
                ? relationships.getOrDefault(profile.id(), session.playerId(),
                        profile.defaultDisposition()) : prefetched.relationship();
        List<MemoryRecord> recent = !routed.includes("MEMORIES") ? List.of()
                : cognition != null && cognition.context() != null
                        ? cognition.context().memories()
                        : memories.relevant(profile.id(), session.playerId(), playerMessage,
                                recentMemoryCount);
        String memoryText = recent.isEmpty()
                ? "None."
                : recent.stream()
                        .map(memory -> "- [" + memory.type() + ", confidence="
                                + "%.2f".formatted(memory.confidence()) + ", source="
                                + memory.source() + "] " + memory.timestamp() + ": "
                                + memory.summary())
                        .collect(Collectors.joining("\n"));
        String recentConversation = routed.includes("RECENT_CONVERSATION")
                ? prefetched != null && routed.depth() == CognitiveDepth.SIMPLE_SOCIAL
                        ? prefetched.recentConversation()
                        : session.recentConversationBlock(profile.name(),
                                routed.depth() == CognitiveDepth.SIMPLE_SOCIAL ? 2 : 6)
                : "Omitted by intent-scoped routing.";
        if (!session.deferredConversationContext().isBlank()) {
            recentConversation += "\nINTERRUPTED CONVERSATION CONTEXT (transient; answer the "
                    + "current message first): " + session.deferredConversationContext();
        }
        if (routed.depth() == CognitiveDepth.DIRECT_FACT
                || routed.depth() == CognitiveDepth.SIMPLE_SOCIAL) {
            String relationshipText = routed.includes("PLAYER_RELATIONSHIP")
                    ? relationship.naturalSummary("the focused player")
                    : "Omitted by intent-scoped routing.";
            boolean preferencesRelevant = routed.detectedIntent().contains("SUBJECTIVE")
                    || profileFactsRelevant(profile, playerMessage);
            String preferences = preferencesRelevant
                    ? "likes=" + boundedList(profile.likes(), 4, 180)
                            + "; dislikes=" + boundedList(profile.dislikes(), 4, 180)
                            + "; goals=" + boundedList(profile.goals(), 3, 160)
                    : "not relevant to this turn";
            String system = """
                    You are %s, a persistent Hytale NPC. Answer the player's current message
                    directly in one or two natural, concise sentences. Output only spoken words;
                    never output reasoning, labels, IDs, diagnostics, or markdown. Stay in
                    character. Do not invent a world fact, memory, relationship, object, event,
                    action, or promise. Mandatory facts may be naturalized but not changed.
                    Intent=%s. Mandatory facts=%s
                    Identity=%s. Personality=%s. Speaking style=%s.
                    Player relationship=%s. Relevant preferences=%s.
                    Recent delivered dialogue=%s
                    """.formatted(profile.name(), routed.detectedIntent(),
                    compactField(routed.constraintBlock(), 420),
                    compactField(profile.selfIdentity(), 140),
                    compactField(profile.personality(), 360),
                    compactField(profile.speakingStyle(), 220),
                    compactField(relationshipText, 220), preferences,
                    compactField(recentConversation, 520));
            return new LlmRequest(session.sessionId(), profile.id(), session.playerId(),
                    List.of(new ChatMessage("system", system),
                            new ChatMessage("user", playerMessage)), List.of());
        }
        String taskText = routed.includes("TASKS") ? requestState.activeTasks().stream()
                .limit(4)
                .map(ConversationContextBuilder::taskFact)
                .collect(Collectors.collectingAndThen(Collectors.joining("\n"),
                        value -> value.isBlank() ? "None." : value))
                : "Omitted by intent-scoped routing.";
        String questText = routed.includes("TASKS") ? requestState.activeQuests().stream().limit(4)
                .map(quest -> "- " + natural(quest.questType().name()) + " is "
                        + natural(quest.status().name()) + ": " + quest.storySummary())
                .collect(Collectors.collectingAndThen(Collectors.joining("\n"),
                        value -> value.isBlank() ? "None." : value))
                : "Omitted by intent-scoped routing.";
        String sharedPlanText = sharedPlans == null || !routed.includes("SHARED_PLANS") ? "None."
                : sharedPlans.activeFor(profile.id()).stream()
                        .filter(plan -> plan.involves(session.playerId()))
                        .limit(3)
                        .map(plan -> "- " + plan.contextSummary(
                                profile.id(), session.playerId()))
                        .collect(Collectors.collectingAndThen(Collectors.joining("\n"),
                                value -> value.isBlank() ? "None." : value));
        boolean heldItemRelevant = referencesHeldItem(playerMessage)
                || (isContextualFollowUp(playerMessage)
                        && referencesHeldItem(recentConversation));
        SemanticWorldModel semanticWorld = routed.includes("SEMANTIC_WORLD")
                && cognition != null && cognition.context() != null
                && cognition.context().semanticWorld() != null
                        ? cognition.context().semanticWorld()
                        : new SemanticPerceptionNormalizer().normalize(
                                RawPerceptionSnapshot.fromLegacy(null, perception),
                                profile, playerMessage);
        String perceptionText = perception.npcEntityId() == null
                ? semanticWorld.promptBlock(playerMessage, heldItemRelevant)
                        + "\nMinimal fallback: " + worldContext.describe()
                : semanticWorld.promptBlock(playerMessage, heldItemRelevant);
        ConversationSession.PlayerUtteranceContext utteranceContext =
                session.playerUtteranceContext();
        if (utteranceContext != null && utteranceContext.remoteHail()) {
            perceptionText += "\nREMOTE HAIL (authoritative semantic metadata): The player "
                    + "directly called to you from outside ordinary conversation range. From "
                    + "the player's perspective you are " + utteranceContext.distanceBand()
                    + " to the " + utteranceContext.directionFromPlayer() + ". Answer from your "
                    + "actual current surroundings. Use an exact building, room, or volume name "
                    + "only if CURRENT PERCEPTION explicitly supplies that name; otherwise use "
                    + "the grounded approximate direction/distance or say you are nearby. "
                    + "Projection=" + utteranceContext.projection()
                    + "; projection is performance metadata and must not change the words.";
        }

        boolean roleRelevant = routed.includes("ACTIONS")
                && (!tools.isEmpty() || asksAboutRole(playerMessage));
        boolean profileRelevant = roleRelevant || profileFactsRelevant(profile, playerMessage);
        String roleContext = !roleRelevant
                ? "Occupation and capabilities omitted because they are irrelevant to this "
                        + "ordinary conversational turn. Eligible actions: none."
                : "Background role: " + profile.role()
                        + "\nEligible registered actions: " + tools.stream()
                                .map(tool -> tool.function().name())
                                .collect(Collectors.joining(", "));
        String identity = profile.selfIdentity() == null
                        || profile.selfIdentity().isBlank()
                        || profile.selfIdentity().equalsIgnoreCase(profile.name())
                ? "" : "\nIdentity detail: " + profile.selfIdentity();
        String profilePersonality = profileRelevant ? profile.personality()
                : "Omitted because no authored personality topic is relevant to this turn.";
        String profilePurpose = profileRelevant ? profile.purpose() : "Omitted as irrelevant.";
        String profileLikes = profileRelevant ? String.join(", ", profile.likes()) : "Omitted.";
        String profileDislikes = profileRelevant
                ? String.join(", ", profile.dislikes()) : "Omitted.";
        String profileValues = profileRelevant ? String.join(", ", profile.values()) : "Omitted.";
        String profileFears = profileRelevant ? String.join(", ", profile.fears()) : "Omitted.";
        String profileGoals = profileRelevant ? String.join(", ", profile.goals()) : "Omitted.";
        String fictionalContext = requestState.mode() == DialogueMode.FICTIONAL_STORY
                ? "Mode=FICTIONAL_STORY. Clearly frame the reply as a fictional story or tale. "
                        + "Story events are not current world state and must not be presented as "
                        + "happening to the player or NPC now. Do not turn story details into an "
                        + "action, task, quest, memory, destination, item, or relationship fact."
                : "None. Do not introduce fictional scene framing into this turn.";
        String proposedPlan = requestState.mode() == DialogueMode.PROPOSED_PLAN
                ? "The player invited a hypothetical plan. Use conditional language such as "
                        + "could, might, would, or if. Nothing is underway until server validation."
                : "None.";
        String environmentRule = requestState.mode() == DialogueMode.ENVIRONMENT_QUERY
                ? "This is an environment query. Answer from CURRENT ENVIRONMENT first, then "
                        + "CURRENT ENTITY/ITEM PERCEPTION. Mention important objects before "
                        + "generic terrain and use the supplied approximate direction/distance. "
                        + "If the snapshot is insufficient, admit uncertainty."
                : "Use environment facts only when they help answer this turn.";
        String selfModel = semanticWorld.selfState().promptBlock();
        String appraisal = cognition == null ? "No special appraisal required."
                : cognition.appraisal().compact();
        String responsePlan = cognition == null ? "Respond naturally."
                : "attention=" + cognition.responsePlan().attentionActions()
                        + "; emote=" + cognition.responsePlan().emote()
                        + "; followUpGuidance=" + cognition.responsePlan().followUpQuestion()
                        + "; vocalEmotion="
                        + cognition.responsePlan().vocalState().emotion()
                        + "; vocalGuidance=" + LysanderVoiceBehavior.guidance(
                                profile, cognition.responsePlan().vocalState())
                        + "; never write a nonverbal event tag or stage direction in dialogue"
                        + "; authorizedGameAction="
                        + (cognition.responsePlan().requestedGameAction().isBlank()
                                ? "NONE" : cognition.responsePlan().requestedGameAction());
        String groundedDecision = cognition == null || cognition.decision() == null
                ? "Legacy request: no structured decision."
                : "Selected intent: " + natural(cognition.decision().selectedIntent().name())
                        + ". Intent directive: "
                        + intentDirective(cognition.decision().selectedIntent())
                        + ". Belief updates: "
                        + cognition.decision().beliefUpdates().stream()
                                .map(belief -> belief.subject() + " "
                                        + natural(belief.predicate()) + ": "
                                        + belief.proposition()).toList()
                        + ". Eligible requested actions: "
                        + cognition.decision().actionRequests()
                        + ". Unknown present facts: " + (cognition.context() == null
                                ? List.of() : cognition.context().unknownWorldFacts().stream()
                                        .map(ConversationContextBuilder::natural).toList()) + ".";

        boolean compactSemanticMode = requestState.mode() == DialogueMode.ORDINARY_CONVERSATION
                || requestState.mode() == DialogueMode.CURRENT_WORLD_STATE
                || requestState.mode() == DialogueMode.ENVIRONMENT_QUERY
                || requestState.mode() == DialogueMode.NPC_INITIATED_CURIOSITY;
        if (preferCompactOrdinaryPrompt && compactSemanticMode && tools.isEmpty()) {
            String compactSystem = """
                    You are %s, one persistent Hytale NPC. Stay in character and answer the
                    player's current message directly, naturally, and concisely. The GROUNDED
                    DECISION was selected before wording; phrase that intent without changing it.
                    Do not use generic assistant language such as "What would you like to explore
                    next?" or "How can I help you?". Do not expose
                    hidden reasoning. Output only words the NPC naturally speaks. Never output
                    timestamps, bracketed records, provenance labels, status fields, belief
                    summaries, or headings from this prompt. Current perception is authoritative for present-world
                    claims; memory and profile are not current-world evidence. Never invent an
                    object, place, event, action, quest, or relationship. Do not discuss forging
                    or blacksmithing unless the current message, perception, task, or relevant
                    memory makes it relevant. The server owns actions and state; only an action
                    explicitly listed in GROUNDED DECISION is authorized.

                    PRIVATE APPRAISAL: %s
                    RESPONSE PLAN: %s
                    GROUNDED DECISION: %s
                    CURRENT PERCEPTION: %s
                    CONTENT CONSTRAINT: %s
                    RECENT CONVERSATION: %s
                    INVALIDATED INTENTS: %s
                    ACTIVE TASKS: %s
                    ACTIVE QUESTS: %s
                    ACTIVE/UPCOMING SHARED PLANS: %s
                    RELEVANT MEMORIES: %s
                    RELATIONSHIP: %s
                    PERSONALITY: %s
                    SPEAKING STYLE: %s
                    PURPOSE: %s
                    LIKES/DISLIKES: %s / %s
                    """.formatted(profile.name(), appraisal, responsePlan, groundedDecision,
                    perceptionText, grounding.contextConstraint(), recentConversation,
                    session.invalidatedIntentBlock(), taskText, questText, sharedPlanText,
                    memoryText,
                    relationship.naturalSummary("the focused player"), profilePersonality,
                    profile.speakingStyle(), profilePurpose, profileLikes, profileDislikes);
            compactSystem += "\nCOGNITIVE DEPTH: " + routed.depth()
                    + "\nDETECTED INTENT: " + routed.detectedIntent()
                    + "\nINCLUDED CONTEXT: " + routed.includedSections()
                    + "\nEXCLUDED CONTEXT: " + routed.excludedSections()
                    + "\nAUTHORITATIVE CONSTRAINTS:\n" + routed.constraintBlock();
            return new LlmRequest(session.sessionId(), profile.id(), session.playerId(),
                    List.of(new ChatMessage("system", compactSystem),
                            new ChatMessage("user", playerMessage)), tools);
        }

        String system = """
                You are %s, one Hytale NPC. Stay in character and answer concisely.
                DIALOGUE_MODE=%s
                Answer directly without a reasoning trace or hidden-work narration.
                The GROUNDED DECISION below was selected before wording. Produce only its spoken
                response; do not replace its intent, beliefs, action request, or evidence. Never
                output timestamps, bracketed records, provenance labels, status fields, belief
                summaries, or headings copied from this prompt. Never
                use generic assistant phrases such as "What would you like to explore next?",
                "How can I help you?", or "Is there anything else?".
                The final user-role message is the current player message and has highest priority.
                Respond to it with natural dialogue. For greetings and ordinary social questions,
                answer with a complete conversational phrase or sentence. Never answer with only
                your own name unless the player asks for your name.
                Current perception is authoritative, but do not mention perceived details unless
                they help answer the current player message.
                Desires and preferences are non-authoritative. Respond directly to the player's
                latest statement. Do not repeatedly request an item, action, or service that
                current authoritative game state says is unavailable. If a preference cannot be
                satisfied, acknowledge the constraint and choose a reasonable available
                alternative or say that you have no alternative.
                A concrete alternative must appear in AVAILABLE/RELEVANT ITEMS below. If that
                list is empty, do not invent an alternative item, container, source, or service.
                Do not evade an unavailable category by suggesting a subtype, synonym, or a
                container holding it. When the player refers to "this", "it", or an offered held
                item, explicitly identify the authoritative held item's display name.
                Keep these categories strictly distinct: CURRENT_WORLD_STATE,
                PROFILE/BACKSTORY, MEMORY, FICTIONAL_STORY, PROPOSED_PLAN,
                VALIDATED_ACTIVE_TASK, and VALIDATED_QUEST.
                Never describe an invented event, destination, movement, object, relationship,
                environmental condition, or quest as currently happening unless authoritative
                Hytale state below confirms it. Creative storytelling is allowed only in
                FICTIONAL_STORY mode and must be clearly framed as fictional. A proposed plan is
                hypothetical and cannot be spoken as already occurring. Do not convert a
                fictional story or proposed plan into a real task, quest, memory, world fact,
                destination, item, relationship change, or completed action.
                When describing the present environment, use CURRENT ENVIRONMENT and CURRENT
                ENTITY/ITEM PERCEPTION as authoritative. Do not invent forests, buildings,
                landmarks, weather, objects, terrain, destinations, or structures that are not
                supported by those facts. %s
                Statements such as "we're going", "I'm taking you", "I'm waiting here",
                "we're crossing", "I'm following you", or "the quest is underway" are allowed
                only when VALIDATED_ACTIVE_TASK, VALIDATED_QUEST, or VALIDATED_SHARED_PLAN below
                confirms the claim and current status supports it.
                World names, server labels, entity labels, and account usernames are not the
                player's stated name. Use a player name only when the player explicitly states it
                in recent conversation or it appears as a factual player memory.
                Do not bring up forging, blacksmithing, weapons, or a forge unless they are
                relevant to the player's message, current perception, an active task, or a
                relevant memory. Do not invent nearby objects or activities.
                The game server, not you, owns identity, memory, inventory, money, movement,
                relationships, quests, and world state. Use a registered function only when the
                player is requesting a physical action. Never claim an action happened before
                the server returns its result. Registered function names are exact immutable IDs;
                never rename them. The PRIVATE APPRAISAL is a compact derived conclusion, not a
                reasoning transcript. Never quote or narrate it. Respect actionAuthorized: do not
                call a function when false. When true and you agree to the request, call the exact
                registered function rather than merely promising. Ask the suggested follow-up only
                when it naturally resolves real uncertainty. Otherwise reply with concise
                in-character dialogue.
                Return one coherent paragraph with normal punctuation and no dialogue list or
                newline-separated fragments. Usually use one to three natural sentences. You may
                ask at most one concise follow-up question when it is genuinely relevant to the
                player's message or authoritative context. Do not ask a question every turn, and
                do not repeat a topic or question from the supplied recent dialogue. Avoid theatrical
                interjections and excessive exclamation marks.
                In NPC_INITIATED_CURIOSITY mode, the final user-role message is an internal event,
                not words spoken by the player. Ask one grounded, context-relevant question only
                when current perception, recent memory, weather, location, or a visible item
                provides a real topic. Never interrupt danger, combat, work, or an active request.

                CURRENT PLAYER MESSAGE:
                Supplied once as the final user-role message after this context.

                NPC SELF-MODEL (identity is stable; runtime state is derived):
                %s

                PRIVATE APPRAISAL (structured conclusion; never reveal hidden reasoning):
                %s

                RESPONSE PLAN (bounded social presentation and authorized action):
                %s

                GROUNDED DECISION (authoritative concise semantic result):
                %s

                CURRENT_WORLD_STATE (authoritative Hytale perception only):
                The HELD_ITEM block was sampled from the selected hotbar slot for this request.
                It overrides earlier conversation or memory claims about what the player holds.
                %s

                %s

                CONTENT EXISTENCE VALIDATION:
                requested/desire=%s
                contentValidation=%s
                contextConstraint=%s

                PLAYER-PROVIDED CLAIM (not authoritative unless confirmed above):
                %s

                AVAILABLE/RELEVANT ITEMS (bounded, not the full registry):
                %s

                RECENT CONVERSATION (same player + NPC + session, oldest to newest;
                fictional turns are labeled and are not current-world evidence):
                %s

                RECENT INVALIDATED/FAILED INTENTS (session-only; do not repeat):
                %s

                VALIDATED_ACTIVE_TASK (server-confirmed current execution only):
                %s

                VALIDATED_QUEST (server-confirmed ACTIVE quest only):
                %s

                VALIDATED_SHARED_PLAN (persistent purpose/participants/time/status):
                %s

                MEMORY (strictly filtered; a remembered claim is not current perception):
                %s

                RELATIONSHIP (deterministic state):
                %s

                PROFILE/BACKSTORY (authored character information, not current events):
                Character: %s%s
                Traits: %s
                Speaking style: %s
                Biography/background: %s
                Purpose: %s
                Likes: %s
                Dislikes: %s
                Values: %s
                Fears: %s
                Goals: %s

                ROLE/CAPABILITIES (background and action eligibility only; not a default topic):
                %s

                PROPOSED_PLAN:
                %s

                FICTIONAL_STORY:
                %s

                OPTIONAL DIRECTOR CONTEXT:
                %s
                """.formatted(
                profile.name(),
                requestState.mode(),
                environmentRule,
                selfModel, appraisal, responsePlan, groundedDecision,
                heldItemRelevant ? perception.heldItemFacts()
                        : "HELD_ITEM: omitted as irrelevant to this turn",
                perceptionText,
                grounding.requestedOrDesiredThing().isBlank()
                        ? "none" : grounding.requestedOrDesiredThing(),
                grounding.contentValidation(), grounding.contextConstraint(),
                grounding.playerClaim().isBlank() ? "None." : grounding.playerClaim(),
                grounding.availableRelevantItems().isEmpty()
                        ? "None perceived or relevant."
                        : grounding.availableRelevantItems().stream()
                                .map(item -> "- " + item).collect(Collectors.joining("\n")),
                recentConversation, session.invalidatedIntentBlock(), taskText, questText,
                sharedPlanText, memoryText,
                relationship.naturalSummary("the focused player")
                        + " completedInteractions=" + relationship.interactionCount(),
                profile.name(), identity, profilePersonality,
                profile.speakingStyle(), roleRelevant ? profile.biography()
                        : "Omitted for this ordinary conversational turn.", profilePurpose,
                profileLikes, profileDislikes, profileValues, profileFears, profileGoals,
                roleContext, proposedPlan, fictionalContext,
                requestState.directorContextIncluded()
                        ? "Included because this turn has an explicitly validated director event."
                        : "None. No Director framing is injected into this dialogue turn.");
        system += "\nCOGNITIVE DEPTH: " + routed.depth()
                + "\nDETECTED INTENT: " + routed.detectedIntent()
                + "\nINCLUDED CONTEXT: " + routed.includedSections()
                + "\nEXCLUDED CONTEXT: " + routed.excludedSections()
                + "\nAUTHORITATIVE CONSTRAINTS:\n" + routed.constraintBlock();

        return new LlmRequest(session.sessionId(), profile.id(), session.playerId(),
                List.of(new ChatMessage("system", system),
                        new ChatMessage("user", playerMessage)),
                tools);
    }

    public DialogueRequestState requestState(
            ConversationSession session, NpcProfile profile, String playerMessage) {
        List<NpcTask> activeTasks = tasks == null ? List.of()
                : tasks.activeFor(profile.id()).stream()
                        .filter(task -> task.requesterPlayerId().equals(session.playerId()))
                        .filter(task -> task.state() == NpcTaskState.ACTIVE
                                || task.state() == NpcTaskState.TRAVELING
                                || task.state() == NpcTaskState.WAITING)
                        .toList();
        var activeQuests = quests == null ? List.<com.inigmasgames.persistentnpcs.quest.DynamicQuest>of()
                : quests.activeForPlayer(session.playerId()).stream()
                        .filter(quest -> quest.issuerNpcId().equals(profile.id()))
                        .filter(quest -> quest.status() == QuestStatus.ACTIVE)
                        .toList();
        DialogueMode mode = DialogueMode.classify(
                playerMessage, !activeTasks.isEmpty(), !activeQuests.isEmpty());
        // No DynamicQuestDirector prompt is injected by ordinary conversation. A future
        // explicit, validated autonomy event may set this through a dedicated call surface.
        return new DialogueRequestState(mode, activeTasks, activeQuests, false);
    }

    public void prefetchStatic(ConversationSession session, NpcProfile profile) {
        if (session == null || profile == null) return;
        RelationshipRecord relationship = relationships.getOrDefault(profile.id(),
                session.playerId(), profile.defaultDisposition());
        staticPrefetch.put(session.sessionId(), new StaticPrefetch(profile.id(),
                session.playerId(), relationship,
                session.recentConversationBlock(profile.name(), 2),
                System.nanoTime()));
    }

    private record StaticPrefetch(UUID npcId, UUID playerId,
            RelationshipRecord relationship, String recentConversation, long capturedNanos) {
        private boolean matches(NpcProfile profile, ConversationSession session) {
            return npcId.equals(profile.id()) && playerId.equals(session.playerId())
                    && System.nanoTime() - capturedNanos
                            <= java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
        }
    }

    private static boolean asksAboutRole(String message) {
        String text = message == null ? "" : message.toLowerCase(java.util.Locale.ROOT);
        return text.contains("your job") || text.contains("your work")
                || text.contains("do for a living") || text.contains("occupation")
                || text.contains("blacksmith") || text.contains("forge")
                || text.contains("craft") || text.contains("repair");
    }

    private static boolean profileFactsRelevant(NpcProfile profile, String message) {
        String text = message == null ? "" : message.toLowerCase(java.util.Locale.ROOT);
        if (text.isBlank()) return false;
        if (text.contains("how do you feel") || text.contains("what do you feel")
                || text.contains("what do you want") || text.contains("your goal")
                || text.contains("your fear") || text.contains("what do you like")
                || text.contains("what do you dislike") || text.contains("your personality")
                || text.contains("tell me about yourself")) return true;
        return java.util.stream.Stream.of(profile.likes(), profile.dislikes(), profile.values(),
                        profile.fears(), profile.goals(), profile.knowledgeDomains())
                .filter(java.util.Objects::nonNull).flatMap(List::stream)
                .filter(java.util.Objects::nonNull)
                .flatMap(value -> java.util.Arrays.stream(
                        value.toLowerCase(java.util.Locale.ROOT).split("[^a-z0-9]+")))
                .filter(word -> word.length() >= 4).anyMatch(text::contains);
    }

    private static boolean referencesHeldItem(String message) {
        String text = message == null ? "" : message.toLowerCase(java.util.Locale.ROOT);
        return text.contains("holding") || text.contains("held item")
                || text.contains("in my hand") || text.contains("this item")
                || text.contains("that item") || text.contains("sword")
                || text.contains("weapon") || text.contains("equip")
                || text.contains("unequip") || text.contains("give me")
                || text.contains("take this") || text.contains("pick up")
                || text.contains("bring me") || text.contains("drop")
                || text.contains("want this") || text.contains("offer this")
                || text.contains("offering") || text.contains("want it")
                || text.contains("have this");
    }

    private static boolean isContextualFollowUp(String message) {
        String text = message == null ? "" : message.toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9 ]", " ").replaceAll("\\s+", " ").strip();
        return text.equals("and now") || text.equals("what about now")
                || text.equals("how about now");
    }

    private static String taskFact(NpcTask task) {
        return "- " + natural(task.type()) + " is " + natural(task.state().name())
                + "; purpose: " + task.purpose()
                + (task.targetX() == null ? "; no resolved destination"
                        : "; destination is resolved by the server");
    }

    private static String natural(String value) {
        return value == null || value.isBlank() ? "none"
                : value.replace('_', ' ').toLowerCase(java.util.Locale.ROOT);
    }

    private static String intentDirective(
            com.inigmasgames.persistentnpcs.cognition.GroundedIntent intent) {
        if (intent == null) return "respond naturally";
        return switch (intent) {
            case REPORT_KNOWN_NPC_LOCATION ->
                    "report only the semantic distance and direction from the locator";
            case OFFER_GUIDE_TO_NPC ->
                    "report the semantic location and make one concise, in-character offer to lead the player";
            case GUIDE_PLAYER_TO_NPC ->
                    "agree concisely and tell the player to remain nearby while native guidance begins";
            case REPORT_UNABLE_TO_LOCATE ->
                    "state the locator limitation honestly without guessing or revealing diagnostics";
            case RESPOND_TO_REMOTE_HAIL ->
                    "call back concisely from the authoritative current location; never invent a place name";
            default -> "phrase the selected grounded intent without changing it";
        };
    }

    private static String compactField(String value, int maximum) {
        String text = value == null ? "" : value.replaceAll("\\s+", " ").strip();
        if (text.isBlank()) return "none";
        return text.length() <= maximum ? text : text.substring(0, maximum).stripTrailing();
    }

    private static String boundedList(List<String> values, int maximumItems,
            int maximumCharacters) {
        String text = (values == null ? List.<String>of() : values).stream()
                .filter(value -> value != null && !value.isBlank()).limit(maximumItems)
                .map(String::strip).collect(Collectors.joining(", "));
        return compactField(text, maximumCharacters);
    }

    /** E3 extension of the sole live prompt renderer. */
    public LlmRequest applyEpistemicContract(LlmRequest request, EpistemicContract contract) {
        return applyEpistemicContract(request, contract, null, "", false);
    }

    /** E3 compact replacement prompt; the final budget planner sees this exact result. */
    public LlmRequest applyEpistemicContract(LlmRequest request, EpistemicContract contract,
            NpcProfile profile, String recentConversation, boolean appendToStructuredPrompt) {
        if (request == null || contract == null
                || contract.mode() != EpistemicFeatureMode.AUTHORITATIVE) return request;
        String propositions = contract.answerPlan().authorizedPropositions().isEmpty()
                ? "none"
                : contract.answerPlan().authorizedPropositions().stream().limit(4)
                        .map(ConversationContextBuilder::promptSafe)
                        .collect(Collectors.joining(" | "));
        String persona = profile == null ? "Stay in the established character."
                : "Character=" + promptSafe(profile.name()) + "; personality="
                        + compactField(promptSafe(profile.personality()), 220) + "; style="
                        + compactField(promptSafe(profile.speakingStyle()), 140) + ".";
        String recent = recentConversation == null || recentConversation.isBlank() ? "none"
                : compactField(promptSafe(recentConversation), 280);
        String block = """
                You are an ImmersiveNPC. Output only one concise spoken reply, no labels or markup.
                %s
                EPISTEMIC ANSWER PLAN: query=%s; answer=%s; certainty=%s; required=%s; action=%s.
                Authorized objective facts=%s.
                Unsupported properties=%s. Recent delivered dialogue=%s.
                Answer the requested slot in the first clause. Assert no objective fact beyond the
                authorized facts. If unknown, say so naturally; if conflicted, preserve conflict;
                capability is never execution. Subjective opinion, emotion, humor, metaphor, and
                explicit hypotheticals are allowed but cannot introduce biography or world facts.
                Keep character and speaking style. Maximum sentences=%d; objective claims=%d.
                """.formatted(persona, contract.queryPlan().queryKind(),
                contract.answerPlan().answerKind(),
                contract.answerability() + "/" + promptSafe(contract.answerPlan().uncertaintyMode()),
                contract.answerPlan().requiredSlots(),
                contract.answerPlan().requestedAction().isBlank() ? "none"
                        : promptSafe(contract.answerPlan().requestedAction()), propositions,
                contract.answerPlan().unsupportedRequestedProperties(), recent,
                contract.answerPlan().maxSentences(), contract.answerPlan().maxObjectiveClaims());
        return appendToStructuredPrompt ? request.withSystemInstruction(block)
                : request.withSystemReplacement(block);
    }

    private static String promptSafe(String value) {
        return value == null ? "" : value.replaceAll("[\\r\\n\\t]+", " ")
                .replaceAll("(?i)(?:system|assistant|developer)\\s*:", "speaker:")
                .replaceAll("[<>`]", "").replaceAll("\\s+", " ").strip();
    }
}
