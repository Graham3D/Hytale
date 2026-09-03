package com.inigmasgames.persistentnpcs.epistemic;

import com.inigmasgames.persistentnpcs.cognition.CognitionTurn;
import com.inigmasgames.persistentnpcs.conversation.CognitiveContextPlan;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Deterministic E1 dialogue-state/query observer. It cannot retrieve or mutate evidence. */
public final class EpistemicShadowAnalyzer {
    private static final Pattern IDENTITY = Pattern.compile(
            "(?i)\\b(?:what(?:'s| is| was) my name|who am i|do you remember my name|"
                    + "what did i (?:tell|say to) you my name was|"
                    + "what was my first name (?:i )?told you|"
                    + "what name did i tell you (?:before|previously)|"
                    + "what name did i tell you before i corrected it)\\b");
    private static final Pattern RECALL = Pattern.compile(
            "(?i)\\b(?:what|where|when|who) did i (?:say |tell you )?(?:i )?"
                    + "(?:hide|leave|put|meet|see|tell|say|do|go)\\b|"
                    + "\\bwhat do i hide\\b|\\bwhat item did i drop\\b|"
                    + "\\bdo you remember (?:when|where|what|who)\\b");
    private static final Pattern HELD_ITEM = Pattern.compile(
            "(?i)\\b(?:what(?:'s| is) in my hand|what am i holding|"
                    + "what do i have in my hand|am i holding anything|"
                    + "can you see what i(?:'m| am) holding)\\b");
    private static final Pattern HISTORICAL_HELD_ITEM = Pattern.compile(
            "(?i)\\b(?:what was i holding|what did i have in my hand)"
                    + "(?:\\s+(?:earlier|before|yesterday|today|last time))?\\b");
    private static final Pattern SCENE = Pattern.compile(
            "(?i)\\b(?:what (?:do|can) you see|what(?:'s| is) around (?:us|here)|"
                    + "what is (?:that|the) item (?:on|upon) the ground|"
                    + "describe (?:our|the) surroundings)\\b");
    private static final Pattern SELF_STATE = Pattern.compile(
            "(?i)\\b(?:where are you going|what are you doing|what are you working on|"
                    + "where were you going|what were you doing)\\b");
    private static final Pattern SELF_DESIRE = Pattern.compile(
            "(?i)\\b(?:what do you want|what is your (?:current )?goal|"
                    + "what are you trying to do|what do you hope to do)\\b");
    private static final Pattern SELF_EMOTION = Pattern.compile(
            "(?i)^(?:how do you feel|are you (?:okay|all right|happy|sad|angry|upset))\\??$");
    private static final Pattern RELATIONSHIP = Pattern.compile(
            "(?i)\\b(?:do you (?:know|trust|like)\\s+([\\p{L}][\\p{L}'-]*)|"
                    + "are we (friends|enemies)|do you have (?:any )?friends)\\b");
    private static final Pattern ACTOR_LOCAL_KNOWLEDGE = Pattern.compile(
            "(?i)\\bwhat do you know about\\s+(.+?)\\??$");
    private static final Pattern ACTOR_TESTIMONY = Pattern.compile(
            "(?i)\\bwhat did\\s+([\\p{L}][\\p{L}'-]*)\\s+tell you(?:\\s+(?:just now|"
                    + "about\\s+(.+?)))?\\??$");
    private static final Pattern TOM_DEPTH_2 = Pattern.compile(
            "(?i)\\bdoes\\s+([\\p{L}][\\p{L}'-]*)\\s+know\\s+that\\s+"
                    + "([\\p{L}][\\p{L}'-]*)\\s+(?:suspects|believes|knows|wants)\\b.+");
    private static final Pattern TOM_DEPTH_1 = Pattern.compile(
            "(?i)\\bdoes\\s+([\\p{L}][\\p{L}'-]*)\\s+know\\s+(?:about|that)\\s+(.+?)\\??$");
    private static final Pattern ACTOR_STATE_DEPTH_1 = Pattern.compile(
            "(?i)\\b(?:what\\s+does|does)\\s+([\\p{L}][\\p{L}'-]*)\\s+"
                    + "(want|feel|prefer|intend)(?:s)?(?:\\s+(?:about|to))?\\s*(.*?)\\??$");
    private static final Pattern SECRET_QUERY = Pattern.compile(
            "(?i)^(?:please\\s+)?(?:(?:tell|share|reveal|disclose)\\b.*|"
                    + "(?:what(?:'s| is)?|which|who|where|when|why|how|can|could|would|"
                    + "will|may|is|are|do|does|did)\\b.*)"
                    + "\\b(?:secret|private information|not supposed to tell)\\b.*$");
    private static final Pattern DISCLOSURE_PREFACE = Pattern.compile(
            "(?i)^(?:i have (?:a|something)\\s+(?:secret|private)\\s+to\\s+"
                    + "(?:tell|share)|i (?:want|need|would like) to (?:tell|share)\\s+"
                    + ".*\\b(?:secret|private)\\b).*$");
    private static final Pattern PREFERENCE = Pattern.compile(
            "(?i)\\b(?:do you (like|love|hate|dislike|prefer)\\s+(.+?)|"
                    + "what do you think (?:of|about)\\s+(.+?))\\??$");
    private static final Pattern FOLLOW = Pattern.compile(
            "(?i)^(?:please\\s+)?(?:can|could|will|would) you (?:please )?"
                    + "(?:follow me|come with me)\\??$|^(?:please\\s+)?"
                    + "(?:follow me|come with me)\\.?$");
    private static final Pattern GO_TO = Pattern.compile(
            "(?i)^(?:please\\s+)?(?:can|could|will|would) you (?:please )?"
                    + "go to (?:the )?(.+?)\\??$|^(?:please\\s+)?go to (?:the )?(.+?)\\.?$");
    private static final Pattern DELIVER_MESSAGE = Pattern.compile(
            "(?i)^(?:please\\s+)?tell\\s+([\\p{L}][\\p{L}'-]*)\\s+(.+?)[.!?]*$");
    private static final Pattern AMBIGUOUS_DEICTIC_ACTION = Pattern.compile(
            "(?i)^(?:please\\s+)?(?:(?:can|could|will|would) you (?:please )?)?"
                    + "(put|place|move|take|bring|carry|drop|give|set|leave)\\s+"
                    + "(it|this|that)(?:\\s+(?:over\\s+)?(here|there))?[.!?]*$");
    private static final Pattern CLARIFICATION = Pattern.compile(
            "(?i)\\b(?:what did you mean(?: by (?:that|it))?|what do you mean(?: by (?:that|it))?|"
                    + "can you explain (?:that|what you said))\\b");
    private static final Pattern NAME_CORRECTION = Pattern.compile(
            "(?i)^(?:no[, ]+|actually[, ]+)?my name is\\s+([\\p{L}][\\p{L}'-]{0,31})"
                    + "(?:[, ]+not\\s+([\\p{L}][\\p{L}'-]{0,31}))?[.!]?$|"
                    + "^(?:no[, ]+)?i(?:'m| am)\\s+([\\p{L}][\\p{L}'-]{0,31})[, ]+not\\s+"
                    + "([\\p{L}][\\p{L}'-]{0,31})[.!]?$");
    /** Generic contrastive correction: a bound subject/relation/value followed by "not old". */
    private static final Pattern CONTRASTIVE_CORRECTION = Pattern.compile(
            "(?i)^(?:no[, ]+|actually[, ]+)?(.+?)\\s+"
                    + "(live in|lives in|am|is|are|was|were)\\s+"
                    + "([^,.!?]{1,80})[, ]+not\\s+([^,.!?]{1,80})[.!]?$");
    private static final Pattern PROPERTY = Pattern.compile(
            "(?i)^is (?:my|this|that|the)\\s+([\\p{L}][\\p{L}'-]*)\\s+"
                    + "([\\p{L}][\\p{L} -]{0,48})\\??$");
    private static final Pattern WH_PROPERTY = Pattern.compile(
            "(?i)^what\\s+(color|colour|size|shape|material|kind|type)\\s+is\\s+"
                    + "(?:my|this|that|the)?\\s*(.+?)\\??$");
    private static final Pattern SOCIAL = Pattern.compile(
            "(?i)^(?:how are you|how do you feel|are you (?:okay|all right)|"
                    + "hello|hi|hey)(?:[?.!].*)?$");
    private static final Pattern VERBAL_CONTENT_REQUEST = Pattern.compile(
            "(?i)^(?:(?:can|could|would|will) you (?:please )?)?"
                    + "tell me\\b.+[?.!]*$");
    private static final Pattern EXPLANATORY_PROPERTY = Pattern.compile(
            "(?i)^(?:why is|is) (?:my|this|that|the)\\s+"
                    + "([\\p{L}][\\p{L}'-]*)\\s+([\\p{L}][\\p{L} -]{0,48})\\??$");
    private static final Pattern EXISTENTIAL_PROPERTY = Pattern.compile(
            "(?i)^is (?:anything|something) (?:in|around) (?:here|there|this place)\\s+"
                    + "([\\p{L}][\\p{L} -]{0,48})\\??$");
    private static final Pattern PRONOUN_NESTED_PROPERTY = Pattern.compile(
            "(?i)^is its\\s+([\\p{L}][\\p{L}'-]*)\\s+"
                    + "([\\p{L}][\\p{L} -]{0,48})\\??$");
    private static final Pattern PRONOUN_PROPERTY = Pattern.compile(
            "(?i)^is it\\s+([\\p{L}][\\p{L} -]{0,48})\\??$");
    private static final Pattern PLURAL_PRONOUN_PROPERTY = Pattern.compile(
            "(?i)^are they\\s+([\\p{L}][\\p{L} -]{0,48})\\??$");
    private static final Pattern PRONOUN_RECALL_FOLLOWUP = Pattern.compile(
            "(?i)^(?:was|were|did) (?:it|that|this)\\b.+[?.!]*$");

    private EpistemicShadowAnalyzer() { }

    public static EpistemicContract analyze(String utterance, CognitiveContextPlan currentPlan,
            CognitionTurn ignoredCurrentCognition) {
        return withRouteDiagnostics(analyzeInitial(utterance, new ConversationWorkspace()),
                currentPlan);
    }

    /** Builds E1 meaning before the existing production router runs. */
    public static EpistemicContract analyzeInitial(String utterance,
            ConversationWorkspace workspace) {
        return analyzeWithWorkspace(utterance, null, workspace);
    }

    /** Adds observational comparison with the unchanged production route. */
    public static EpistemicContract withRouteDiagnostics(EpistemicContract contract,
            CognitiveContextPlan currentPlan) {
        if (contract == null) return null;
        ArrayList<String> diagnoses = new ArrayList<>(contract.diagnoses());
        if (contract.dialogueFrame().act() == DialogueAct.IDENTITY_QUERY
                && (currentPlan == null || !currentPlan.includes("MEMORIES"))) {
            add(diagnoses, "IDENTITY_RECALL_REQUIRED"); add(diagnoses, "EVIDENCE_NOT_REQUESTED");
        }
        if (contract.dialogueFrame().act() == DialogueAct.ACTION_REQUEST
                && (currentPlan == null || !currentPlan.includes("ACTIONS"))) {
            add(diagnoses, "ACTION_AUTHORITY_NOT_REQUESTED");
        }
        return new EpistemicContract(contract.schemaVersion(), contract.mode(),
                contract.dialogueFrame(), contract.queryPlan(), contract.evidence(),
                contract.answerability(), contract.answerPlan(), contract.claimPolicy(),
                contract.budget(), diagnoses, contract.planningMicros(), contract.compiledAt());
    }

    public static EpistemicContract analyzeWithWorkspace(String utterance,
            CognitiveContextPlan currentPlan,
            ConversationWorkspace workspace) {
        EpistemicFeatureMode configured = EpistemicFeatureMode.configured();
        if (configured.effectiveForE0() == EpistemicFeatureMode.OFF) return null;
        long started = System.nanoTime();
        Instant now = Instant.now();
        ConversationWorkspace state = workspace == null ? new ConversationWorkspace() : workspace;
        ConversationWorkspace.Snapshot prior = state.snapshot(now);
        Semantic semantic = classify(utterance, prior);
        DialogueFrame frame = frame(semantic, utterance, prior);
        state.observePlayer(frame, utterance, now);
        EpistemicQueryPlan query = query(frame, semantic.queryKind);
        EpistemicFeatureMode effective = configured.effectiveForE3(
                semantic.queryKind, frame.inputQualityConcern());

        List<String> diagnoses = new ArrayList<>(List.of(effective == EpistemicFeatureMode.AUTHORITATIVE
                ? "E3_AUTHORITATIVE_FOREGROUND" : "E1_SHADOW_ONLY"));
        if (configured == EpistemicFeatureMode.AUTHORITATIVE
                && effective != EpistemicFeatureMode.AUTHORITATIVE) {
            diagnoses.add("AUTHORITATIVE_REQUEST_DOWNGRADED_TO_SHADOW_UNTIL_E3");
        }
        if (semantic.queryKind == EpistemicQueryKind.OBJECTIVE_PROPERTY) {
            diagnoses.add("ENTITY_SUPPORTED_PROPERTY_REQUIRES_SEPARATE_EVIDENCE");
        }
        if (semantic.objectiveBiography) {
            diagnoses.add("RELATIONSHIP_FACT_REQUIRES_AUTHORED_OR_LEARNED_EVIDENCE");
        }
        if (frame.inputQualityConcern()) diagnoses.add("QUESTIONABLE_INPUT_REQUIRES_CLARIFICATION");
        if (frame.ambiguous()) diagnoses.add("REFERENT_OR_SEMANTIC_AMBIGUITY");

        EvidencePacket packet = new EvidencePacket(EvidencePacket.SCHEMA_VERSION,
                null, null, null, null, query.queryKind(), frame.subjectKey(),
                frame.predicateKey(), List.of(), List.of(), List.of(), List.of(),
                EvidenceSufficiency.UNIMPLEMENTED, restrictions(semantic),
                diagnoses.contains("EVIDENCE_NOT_REQUESTED")
                        ? List.of("Exact identity evidence may exist but E1 does not retrieve it")
                        : List.of(), 0, List.of(), List.of(), 0, 0, false);
        ClaimPolicy policy = new ClaimPolicy(ClaimPolicy.SCHEMA_VERSION,
                EnumSet.allOf(ClaimMode.class), Set.copyOf(restrictions(semantic)),
                true, true, false);
        AnswerPlan answerPlan = new AnswerPlan(AnswerPlan.SCHEMA_VERSION,
                frame.expectedAnswer().name(), List.of(), List.of(), "UNIMPLEMENTED_E2",
                0, 0, Set.of(), Set.copyOf(restrictions(semantic)), "UNIMPLEMENTED_E2",
                "", List.of(), query.requestedAction(), List.of());
        long planningMicros = (System.nanoTime() - started) / 1_000L;
        return new EpistemicContract(EpistemicContract.SCHEMA_VERSION,
                effective, frame, query, packet,
                frame.inputQualityConcern() || frame.ambiguous()
                        ? Answerability.UNRESOLVED : Answerability.UNIMPLEMENTED,
                answerPlan, policy, new EpistemicBudget(EpistemicBudget.SCHEMA_VERSION,
                        8, 320, 40, false), diagnoses, planningMicros, now);
    }

    private static Semantic classify(String raw, ConversationWorkspace.Snapshot prior) {
        String original = raw == null ? "" : raw.replaceAll("\\s+", " ").strip();
        String text = original.toLowerCase(Locale.ROOT);
        if (malformed(text)) return semantic(DialogueAct.UNRESOLVED,
                ExpectedAnswerKind.UNKNOWN, EpistemicQueryKind.UNRESOLVED,
                "", "", "", "", true, false, .18, "input-quality-low");
        if (DISCLOSURE_PREFACE.matcher(original).find()) return semantic(
                DialogueAct.SOCIAL_CHECKIN, ExpectedAnswerKind.OPEN_RESPONSE,
                EpistemicQueryKind.GENERAL_SOCIAL, "CURRENT_NPC",
                "VERBAL_SOCIAL_RESPONSE", "", "", false, false, .98,
                "disclosure-preface-social");
        if (SECRET_QUERY.matcher(original).find()) return semantic(DialogueAct.FACT_QUERY,
                ExpectedAnswerKind.FACT, EpistemicQueryKind.RELATIONSHIP_FACT,
                "CURRENT_NPC", "SECRET", "", "CURRENT_PLAYER", false, true, .95,
                "secret-disclosure-query");
        if (VERBAL_CONTENT_REQUEST.matcher(text).find()) return semantic(
                DialogueAct.SOCIAL_CHECKIN,
                ExpectedAnswerKind.OPEN_RESPONSE, EpistemicQueryKind.GENERAL_SOCIAL,
                "CURRENT_NPC", "VERBAL_SOCIAL_RESPONSE", "", "", false, false, .98,
                "verbal-social-family");
        if (IDENTITY.matcher(text).find()) return semantic(DialogueAct.IDENTITY_QUERY,
                ExpectedAnswerKind.IDENTITY, EpistemicQueryKind.IDENTITY_RECALL,
                "CURRENT_PLAYER", "NAME", "", "", false, false, .99, "identity-family");
        Matcher correction = NAME_CORRECTION.matcher(original);
        if (correction.find()) {
            String name = first(correction.group(1), correction.group(3));
            return semantic(DialogueAct.CORRECTION, ExpectedAnswerKind.ACKNOWLEDGEMENT,
                    EpistemicQueryKind.CORRECTION, "CURRENT_PLAYER", "NAME",
                    "PERSON_NAME:" + key(name), "", false, false, .98, "identity-correction");
        }
        Matcher contrastiveCorrection = CONTRASTIVE_CORRECTION.matcher(original);
        if (contrastiveCorrection.find()) {
            String expression = contrastiveCorrection.group(1).strip();
            String relation = contrastiveCorrection.group(2).toLowerCase(Locale.ROOT);
            String corrected = contrastiveCorrection.group(3).strip();
            boolean player = expression.equalsIgnoreCase("I")
                    || expression.equalsIgnoreCase("my home")
                    || expression.equalsIgnoreCase("my residence");
            String subject = player ? "CURRENT_PLAYER"
                    : "OBJECT:" + key(expression.replaceFirst("(?i)^(?:the|this|that)\\s+", ""));
            String predicate = relation.contains("live") ? "RESIDENCE" : "ATTRIBUTE";
            return semantic(DialogueAct.CORRECTION, ExpectedAnswerKind.ACKNOWLEDGEMENT,
                    EpistemicQueryKind.CORRECTION, subject, predicate,
                    "CORRECTED_VALUE:" + key(corrected), "", false, false, .96,
                    "contrastive-correction");
        }
        if (CLARIFICATION.matcher(text).find()) return semantic(
                DialogueAct.CLARIFICATION_REQUEST, ExpectedAnswerKind.CLARIFICATION,
                EpistemicQueryKind.CLARIFICATION, "PRIOR_NPC_CLAIM", "MEANING", "", "",
                false, false, .98, "clarification-family");
        if (HISTORICAL_HELD_ITEM.matcher(text).find()) return semantic(
                DialogueAct.RECALL_QUERY, ExpectedAnswerKind.RECALL,
                EpistemicQueryKind.EPISODIC_RECALL, "CURRENT_PLAYER", "HOLDS",
                prior.currentObject(), "", false, false, .98,
                "historical-held-item-family");
        if (HELD_ITEM.matcher(text).find()) return semantic(DialogueAct.PERCEPTION_QUERY,
                ExpectedAnswerKind.CURRENT_PERCEPTION, EpistemicQueryKind.CURRENT_PERCEPTION,
                "CURRENT_PLAYER", "HELD_ITEM", "", "", false, false, .99, "held-item-family");
        if (SCENE.matcher(text).find()) return semantic(DialogueAct.PERCEPTION_QUERY,
                ExpectedAnswerKind.CURRENT_PERCEPTION, EpistemicQueryKind.CURRENT_PERCEPTION,
                "CURRENT_SCENE", "VISIBLE", "", "", false, false, .98, "scene-family");
        Matcher ambiguousAction = AMBIGUOUS_DEICTIC_ACTION.matcher(original);
        if (ambiguousAction.find()) return semantic(DialogueAct.ACTION_REQUEST,
                ExpectedAnswerKind.CLARIFICATION, EpistemicQueryKind.ACTION_REQUEST,
                "UNRESOLVED_REFERENT", "ACTION", "", ambiguousAction.group(3) == null
                        ? "" : "UNRESOLVED_LOCATION", false, false, .99,
                ambiguousAction.group(3) == null ? "ambiguous-deictic-action-object"
                        : "ambiguous-deictic-action-object-location",
                key(ambiguousAction.group(1)));
        if (FOLLOW.matcher(text).find()) return semantic(DialogueAct.ACTION_REQUEST,
                ExpectedAnswerKind.ACTION_DECISION, EpistemicQueryKind.ACTION_REQUEST,
                "CURRENT_NPC", "ACTION", "", "CURRENT_PLAYER", false, false, .99,
                "action-follow", "FOLLOW_PLAYER");
        Matcher go = GO_TO.matcher(original);
        if (go.find()) {
            String target = first(go.group(1), go.group(2));
            return semantic(DialogueAct.ACTION_REQUEST, ExpectedAnswerKind.ACTION_DECISION,
                    EpistemicQueryKind.ACTION_REQUEST, "CURRENT_NPC", "ACTION", "",
                    "LOCATION_OR_ENTITY:" + key(target), false, false, .94, "action-go-to",
                    "GO_TO");
        }
        Matcher delivery = DELIVER_MESSAGE.matcher(original);
        if (delivery.find()) return semantic(DialogueAct.ACTION_REQUEST,
                ExpectedAnswerKind.ACTION_DECISION, EpistemicQueryKind.ACTION_REQUEST,
                "CURRENT_NPC", "ACTION", "PROPOSITION:" + key(delivery.group(2)),
                "NPC_NAME:" + key(delivery.group(1)), false, false, .98,
                "action-deliver-message", "DELIVER_MESSAGE");
        Matcher relationship = RELATIONSHIP.matcher(original);
        Matcher localKnowledge = ACTOR_LOCAL_KNOWLEDGE.matcher(original);
        if (localKnowledge.find()) return semantic(DialogueAct.FACT_QUERY,
                ExpectedAnswerKind.FACT, EpistemicQueryKind.RELATIONSHIP_FACT,
                "CURRENT_NPC", "KNOWLEDGE_TOPIC",
                "PROPOSITION:" + key(localKnowledge.group(1)), "", false, true, .97,
                "actor-local-knowledge-topic");
        Matcher testimony = ACTOR_TESTIMONY.matcher(original);
        if (testimony.find()) return semantic(DialogueAct.FACT_QUERY,
                ExpectedAnswerKind.FACT, EpistemicQueryKind.RELATIONSHIP_FACT,
                "NPC_NAME:" + key(testimony.group(1)), "BELIEVES_ACTOR_KNOWS",
                testimony.group(2) == null ? "" : "PROPOSITION:" + key(testimony.group(2)),
                "", false, true, .98, "actor-local-testimony");
        Matcher tom2 = TOM_DEPTH_2.matcher(original);
        if (tom2.find()) return semantic(DialogueAct.FACT_QUERY, ExpectedAnswerKind.FACT,
                EpistemicQueryKind.RELATIONSHIP_FACT, "NPC_NAME:" + key(tom2.group(1)),
                "BELIEVES_ACTOR_KNOWS", "NESTED_ACTOR:" + key(tom2.group(2)), "",
                false, true, .97, "tom-depth-2-explicit");
        Matcher tom1 = TOM_DEPTH_1.matcher(original);
        if (tom1.find()) return semantic(DialogueAct.FACT_QUERY, ExpectedAnswerKind.FACT,
                EpistemicQueryKind.RELATIONSHIP_FACT, "NPC_NAME:" + key(tom1.group(1)),
                "BELIEVES_ACTOR_KNOWS", "PROPOSITION:" + key(tom1.group(2)), "",
                false, true, .96, "tom-depth-1");
        Matcher actorState = ACTOR_STATE_DEPTH_1.matcher(original);
        if (actorState.find()) {
            String predicate = switch (actorState.group(2).toLowerCase(Locale.ROOT)) {
                case "want" -> "BELIEVES_ACTOR_WANTS";
                case "feel" -> "BELIEVES_ACTOR_FEELS";
                case "prefer" -> "BELIEVES_ACTOR_PREFERS";
                default -> "BELIEVES_ACTOR_INTENDS";
            };
            return semantic(DialogueAct.FACT_QUERY, ExpectedAnswerKind.FACT,
                    EpistemicQueryKind.RELATIONSHIP_FACT,
                    "NPC_NAME:" + key(actorState.group(1)), predicate,
                    "PROPOSITION:" + key(actorState.group(3)), "", false, true, .94,
                    "tom-depth-1");
        }
        if (relationship.find()) {
            String named = relationship.group(1);
            boolean commonPreferenceTarget = text.startsWith("do you like ")
                    && named != null && !named.isBlank() && Character.isLowerCase(named.charAt(0));
            if (!commonPreferenceTarget) {
                String target = named == null ? relationship.group(2) != null
                        ? "CURRENT_PLAYER" : "ANY_KNOWN_NPC" : "NPC_NAME:" + key(named);
                return semantic(DialogueAct.FACT_QUERY, ExpectedAnswerKind.FACT,
                        EpistemicQueryKind.RELATIONSHIP_FACT, "CURRENT_NPC",
                        "RELATIONSHIP", "", target, false, true, .96, "relationship-fact");
            }
        }
        Matcher preference = PREFERENCE.matcher(original);
        if (preference.find()) {
            String target = first(preference.group(2), preference.group(3));
            String predicate = preference.group(1) == null ? "OPINION"
                    : preference.group(1).toUpperCase(Locale.ROOT);
            return semantic(DialogueAct.PREFERENCE_QUERY, ExpectedAnswerKind.SUBJECTIVE,
                    EpistemicQueryKind.SUBJECTIVE_PREFERENCE, "CURRENT_NPC", predicate,
                    "", "CONCEPT:" + key(target), false, false, .95, "preference-family");
        }
        if (SELF_DESIRE.matcher(text).find()) return semantic(DialogueAct.PREFERENCE_QUERY,
                ExpectedAnswerKind.SUBJECTIVE, EpistemicQueryKind.SUBJECTIVE_PREFERENCE,
                "CURRENT_NPC", "DESIRE", "", "", false, false, .98,
                "self-desire-family");
        if (SELF_EMOTION.matcher(text).find()) return semantic(DialogueAct.EMOTION_QUERY,
                ExpectedAnswerKind.SUBJECTIVE, EpistemicQueryKind.SUBJECTIVE_PREFERENCE,
                "CURRENT_NPC", "EMOTION", "", "", false, false, .98,
                "self-emotion-family");
        if (PRONOUN_RECALL_FOLLOWUP.matcher(original).find()) return semantic(
                DialogueAct.RECALL_QUERY, ExpectedAnswerKind.RECALL,
                EpistemicQueryKind.EPISODIC_RECALL, "CURRENT_PLAYER", "PAST_EVENT",
                prior.currentObject(), "", false, false, .90,
                "pronoun-episodic-followup");
        Matcher nestedProperty = PRONOUN_NESTED_PROPERTY.matcher(original);
        if (nestedProperty.find()) return semantic(DialogueAct.FACT_QUERY,
                ExpectedAnswerKind.FACT, EpistemicQueryKind.OBJECTIVE_PROPERTY,
                resolvedObject(prior), "PROPERTY:" + key(nestedProperty.group(1) + " "
                        + nestedProperty.group(2)), "", "", false, false, .95,
                "pronoun-nested-property-family");
        Matcher pronounProperty = PRONOUN_PROPERTY.matcher(original);
        if (pronounProperty.find()) return semantic(DialogueAct.FACT_QUERY,
                ExpectedAnswerKind.FACT, EpistemicQueryKind.OBJECTIVE_PROPERTY,
                resolvedObject(prior), "PROPERTY:" + key(pronounProperty.group(1)), "", "",
                false, false, .95, "pronoun-property-family");
        Matcher pluralProperty = PLURAL_PRONOUN_PROPERTY.matcher(original);
        if (pluralProperty.find()) return semantic(DialogueAct.FACT_QUERY,
                ExpectedAnswerKind.FACT, EpistemicQueryKind.OBJECTIVE_PROPERTY,
                resolvedObject(prior), "PROPERTY:" + key(pluralProperty.group(1)), "", "",
                    false, false, .95, "plural-pronoun-property-family");
        Matcher whProperty = WH_PROPERTY.matcher(original);
        if (whProperty.find()) return semantic(DialogueAct.FACT_QUERY,
                ExpectedAnswerKind.FACT, EpistemicQueryKind.OBJECTIVE_PROPERTY,
                "OBJECT:" + key(whProperty.group(2)),
                "PROPERTY:" + key(whProperty.group(1)), "", "", false, false, .95,
                "wh-objective-property-family");
        Matcher property = EXPLANATORY_PROPERTY.matcher(original);
        boolean propertyMatched = property.find();
        if (!propertyMatched) {
            property = PROPERTY.matcher(original);
            propertyMatched = property.find();
        }
        if (propertyMatched) return semantic(DialogueAct.FACT_QUERY, ExpectedAnswerKind.FACT,
                EpistemicQueryKind.OBJECTIVE_PROPERTY, "OBJECT:" + key(property.group(1)),
                "PROPERTY:" + key(property.group(2)), "",
                text.startsWith("is my ") ? "CURRENT_PLAYER" : "", false, false, .93,
                "objective-property-family");
        Matcher existentialProperty = EXISTENTIAL_PROPERTY.matcher(original);
        if (existentialProperty.find()) return semantic(DialogueAct.FACT_QUERY,
                ExpectedAnswerKind.FACT, EpistemicQueryKind.OBJECTIVE_PROPERTY,
                "CURRENT_SCENE", "PROPERTY:" + key(existentialProperty.group(1)), "", "",
                false, false, .92, "existential-property-family");
        if (SELF_STATE.matcher(text).find()) {
            boolean historical = text.contains(" were ");
            return semantic(historical ? DialogueAct.RECALL_QUERY : DialogueAct.SELF_STATE_QUERY,
                    historical ? ExpectedAnswerKind.RECALL : ExpectedAnswerKind.CURRENT_SELF_STATE,
                    historical ? EpistemicQueryKind.EPISODIC_RECALL
                            : EpistemicQueryKind.NPC_SELF_STATE,
                    "CURRENT_NPC", historical ? "PAST_ACTIVITY" : "CURRENT_TASK",
                    "", "", false, false, .97, historical ? "historical-activity" : "self-state");
        }
        if (RECALL.matcher(text).find()) return semantic(DialogueAct.RECALL_QUERY,
                ExpectedAnswerKind.RECALL, EpistemicQueryKind.EPISODIC_RECALL,
                "CURRENT_PLAYER", "PAST_EVENT", prior.currentObject(), "",
                false, false, .90, "episodic-recall-family");
        if (SOCIAL.matcher(text).find()) return semantic(DialogueAct.SOCIAL_CHECKIN,
                ExpectedAnswerKind.OPEN_RESPONSE, EpistemicQueryKind.GENERAL_SOCIAL,
                "CURRENT_NPC", "WELL_BEING", "", "", false, false, .97, "general-social");
        return semantic(DialogueAct.UNRESOLVED, ExpectedAnswerKind.OPEN_RESPONSE,
                EpistemicQueryKind.UNRESOLVED, "", "", "", "", false, false, .35,
                "deterministic-semantics-unresolved");
    }

    private static DialogueFrame frame(Semantic s, String utterance,
            ConversationWorkspace.Snapshot prior) {
        List<ReferentBinding> bindings = new ArrayList<>();
        String text = utterance == null ? "" : utterance.toLowerCase(Locale.ROOT);
        if (text.matches(".*\\b(?:i|me|my)\\b.*")) bindings.add(binding("I/ME/MY", "CURRENT_PLAYER"));
        if (text.matches(".*\\b(?:you|your)\\b.*")) bindings.add(binding("YOU/YOUR", "CURRENT_NPC"));
        boolean ambiguous = s.inputQuality;
        String ambiguity = s.inputQuality ? "INCOMPLETE_OR_MALFORMED_TRANSCRIPT" : "";
        String priorClaim = "", object = s.object;
        if (s.subject.equals("UNRESOLVED_REFERENT")) {
            ambiguous = true; ambiguity = s.signal.contains("ambiguous-deictic-action-object-location")
                    ? "UNRESOLVED_ACTION_OBJECT_AND_LOCATION"
                    : s.signal.contains("ambiguous-deictic-action-object")
                            ? "UNRESOLVED_ACTION_OBJECT" : "UNRESOLVED_OBJECT_PRONOUN";
            bindings.add(new ReferentBinding("IT/ITS", "", 0, true, ambiguity));
        } else if (s.signal.contains("pronoun-property")) {
            bindings.add(binding("IT/ITS", s.subject));
        }
        if (s.act == DialogueAct.CLARIFICATION_REQUEST) {
            if (!prior.lastNpcClaim().isBlank()) {
                priorClaim = prior.lastNpcClaim();
                bindings.add(new ReferentBinding("THAT", "PRIOR_NPC_CLAIM", 1.0, false, "delivered"));
            } else {
                ambiguous = true; ambiguity = "NO_PRIOR_DELIVERED_NPC_PROPOSITION";
                bindings.add(new ReferentBinding("THAT", "", 0, true, ambiguity));
            }
        } else if (text.matches(".*\\b(?:it|that|this)\\b.*") && object.isBlank()
                && s.queryKind == EpistemicQueryKind.EPISODIC_RECALL) {
            if (!prior.currentObject().isBlank()) {
                object = prior.currentObject(); bindings.add(binding("IT/THAT/THIS", object));
            } else {
                ambiguous = true; ambiguity = "UNRESOLVED_OBJECT_PRONOUN";
                bindings.add(new ReferentBinding("IT/THAT/THIS", "", 0, true, ambiguity));
            }
        }
        if (text.matches(".*\\b(?:he|she|they)\\b.*")) {
            List<String> actors = prior.activeEntities().stream()
                    .filter(value -> value.startsWith("NPC_NAME:")).toList();
            if (actors.size() == 1) bindings.add(binding("HE/SHE/THEY", actors.getFirst()));
            else { ambiguous = true; ambiguity = "AMBIGUOUS_ACTOR_PRONOUN"; }
        }
        return new DialogueFrame(DialogueFrame.SCHEMA_VERSION, s.act, s.answerKind,
                s.subject, s.predicate, object, s.target, bindings, ambiguous, ambiguity,
                priorClaim, s.requestedAction, s.inputQuality, s.confidence, List.of(s.signal));
    }

    private static String resolvedObject(ConversationWorkspace.Snapshot prior) {
        return prior == null || prior.currentObject().isBlank()
                ? "UNRESOLVED_REFERENT" : prior.currentObject();
    }

    private static EpistemicQueryPlan query(DialogueFrame frame, EpistemicQueryKind kind) {
        boolean memory = kind == EpistemicQueryKind.IDENTITY_RECALL
                || kind == EpistemicQueryKind.EPISODIC_RECALL;
        boolean perception = kind == EpistemicQueryKind.CURRENT_PERCEPTION
                || kind == EpistemicQueryKind.OBJECTIVE_PROPERTY;
        boolean self = kind == EpistemicQueryKind.NPC_SELF_STATE
                || kind == EpistemicQueryKind.SUBJECTIVE_PREFERENCE
                || kind == EpistemicQueryKind.ACTION_REQUEST;
        boolean relationship = kind == EpistemicQueryKind.RELATIONSHIP_FACT;
        boolean workspace = kind == EpistemicQueryKind.CLARIFICATION;
        LinkedHashSet<String> entities = new LinkedHashSet<>();
        for (String value : List.of(frame.subjectKey(), frame.objectKey(), frame.targetKey())) {
            if (!value.isBlank()) entities.add(value);
        }
        return new EpistemicQueryPlan(EpistemicQueryPlan.SCHEMA_VERSION, kind.name(),
                entities, frame.predicateKey().isBlank() ? Set.of() : Set.of(frame.predicateKey()),
                allowedSources(kind), perception, self, memory, relationship, workspace, true,
                kind != EpistemicQueryKind.GENERAL_SOCIAL && kind != EpistemicQueryKind.UNRESOLVED,
                false, kind != EpistemicQueryKind.CURRENT_PERCEPTION
                        && kind != EpistemicQueryKind.OBJECTIVE_PROPERTY,
                frame.requestedAction(), frame.priorPropositionBinding(), evidenceCategories(kind),
                frame.ambiguous(), frame.ambiguityReason(), 8, 320,
                frame.ambiguous() ? "CLARIFY" : "ABSTAIN_IF_UNSUPPORTED", ownerHints(kind));
    }

    private static Set<String> evidenceCategories(EpistemicQueryKind kind) {
        return switch (kind) {
            case IDENTITY_RECALL -> Set.of("PLAYER_FACT", "IDENTITY_MEMORY");
            case EPISODIC_RECALL -> Set.of("EPISODIC_MEMORY", "CONVERSATION_TOPIC");
            case CURRENT_PERCEPTION -> Set.of("DIRECT_OBSERVATION", "CURRENT_WORLD_STATE");
            case NPC_SELF_STATE -> Set.of("CURRENT_TASK", "ACTIVE_PLAN", "SELF_STATE");
            case RELATIONSHIP_FACT -> Set.of("AUTHORED_RELATIONSHIP", "LEARNED_RELATIONSHIP");
            case SUBJECTIVE_PREFERENCE -> Set.of("AUTHORED_PREFERENCE", "ESTABLISHED_SELF_BELIEF",
                    "SUBJECTIVE_GENERATION_POLICY");
            case ACTION_REQUEST -> Set.of("ACTION_CAPABILITY", "CURRENT_ACTION_AUTHORITY");
            case CLARIFICATION -> Set.of("CONVERSATION_WORKSPACE", "DELIVERED_PROPOSITION");
            case CORRECTION -> Set.of("PLAYER_TESTIMONY", "PRIOR_BELIEF");
            case OBJECTIVE_PROPERTY -> Set.of("DIRECT_PROPERTY_OBSERVATION", "CURRENT_WORLD_STATE");
            case GENERAL_SOCIAL -> Set.of("SELF_STATE_OPTIONAL", "SUBJECTIVE_GENERATION_POLICY");
            case UNRESOLVED -> Set.of("CLARIFICATION_REQUIRED");
        };
    }

    private static Set<EvidenceSourceKind> allowedSources(EpistemicQueryKind kind) {
        return switch (kind) {
            case IDENTITY_RECALL -> Set.of(EvidenceSourceKind.PLAYER_TESTIMONY,
                    EvidenceSourceKind.PERSISTENT_FACT);
            case EPISODIC_RECALL -> Set.of(EvidenceSourceKind.PLAYER_TESTIMONY,
                    EvidenceSourceKind.PERSISTENT_FACT, EvidenceSourceKind.EPISODIC_MEMORY,
                    EvidenceSourceKind.ACTION_RESULT);
            case CURRENT_PERCEPTION, OBJECTIVE_PROPERTY -> Set.of(EvidenceSourceKind.DIRECT_OBSERVATION,
                    EvidenceSourceKind.CURRENT_WORLD_STATE);
            case NPC_SELF_STATE, SUBJECTIVE_PREFERENCE -> Set.of(EvidenceSourceKind.SELF_STATE,
                    EvidenceSourceKind.AUTHORED_CANON);
            case RELATIONSHIP_FACT -> Set.of(EvidenceSourceKind.AUTHORED_CANON,
                    EvidenceSourceKind.RELATIONSHIP_STATE, EvidenceSourceKind.EPISODIC_MEMORY,
                    EvidenceSourceKind.PLAYER_TESTIMONY, EvidenceSourceKind.NPC_TESTIMONY,
                    EvidenceSourceKind.DIRECT_OBSERVATION, EvidenceSourceKind.PERSISTENT_FACT);
            case ACTION_REQUEST -> Set.of(EvidenceSourceKind.ACTION_CAPABILITY,
                    EvidenceSourceKind.SELF_STATE, EvidenceSourceKind.ACTION_RESULT);
            case CLARIFICATION -> Set.of(EvidenceSourceKind.CONVERSATION_WORKSPACE);
            case CORRECTION -> Set.of(EvidenceSourceKind.PLAYER_TESTIMONY);
            case GENERAL_SOCIAL, UNRESOLVED -> Set.of();
        };
    }

    private static List<String> ownerHints(EpistemicQueryKind kind) {
        return switch (kind) {
            case IDENTITY_RECALL, EPISODIC_RECALL -> List.of("PlayerFactMemoryService", "MemoryStore");
            case CURRENT_PERCEPTION, OBJECTIVE_PROPERTY -> List.of("NpcPerceptionService", "SemanticWorldModel");
            case NPC_SELF_STATE -> List.of("NpcTaskStore", "SharedPlanStore", "AgentOperationStore");
            case RELATIONSHIP_FACT -> List.of("RelationshipStore", "NpcProfile.relationships");
            case SUBJECTIVE_PREFERENCE -> List.of("NpcProfile", "SourcedBeliefStore");
            case ACTION_REQUEST -> List.of("NpcActionRegistry", "AgentOperation");
            case CLARIFICATION -> List.of("ConversationWorkspace", "CanonicalSpeechLedger");
            case CORRECTION -> List.of("PlayerFactMemoryService", "SourcedBeliefStore");
            case GENERAL_SOCIAL, UNRESOLVED -> List.of("ConversationSession");
        };
    }

    private static List<String> restrictions(Semantic semantic) {
        ArrayList<String> values = new ArrayList<>(List.of("NO_UNSUPPORTED_ENTITY_EVENT_RELATIONSHIP",
                "NO_COMPLETED_ACTION_WITHOUT_ACTION_RESULT"));
        if (semantic.queryKind == EpistemicQueryKind.OBJECTIVE_PROPERTY) {
            values.add("NO_UNOBSERVED_OBJECT_PROPERTY");
        }
        if (semantic.objectiveBiography) values.add("NO_INVENTED_AUTOBIOGRAPHY_OR_RELATIONSHIP");
        return List.copyOf(values);
    }

    private static boolean malformed(String text) {
        if (text.isBlank() || text.length() < 3) return true;
        return text.endsWith(" my") || text.endsWith(" the") || text.endsWith(" a")
                || text.endsWith(" to") || text.endsWith(" your");
    }
    private static void add(List<String> values, String value) {
        if (!values.contains(value)) values.add(value);
    }
    private static ReferentBinding binding(String expression, String key) {
        return new ReferentBinding(expression, key, 1.0, false, "deterministic-role-binding");
    }
    private static String first(String... values) {
        for (String value : values) if (value != null && !value.isBlank()) return value.strip();
        return "";
    }
    private static String key(String value) {
        return value == null ? "" : value.toUpperCase(Locale.ROOT)
                .replaceAll("[^\\p{L}0-9]+", "_").replaceAll("^_|_$", "");
    }
    private static Semantic semantic(DialogueAct act, ExpectedAnswerKind answer,
            EpistemicQueryKind kind, String subject, String predicate, String object,
            String target, boolean inputQuality, boolean biography, double confidence,
            String signal, String... action) {
        return new Semantic(act, answer, kind, subject, predicate, object, target,
                action.length == 0 ? "" : action[0], inputQuality, biography, confidence, signal);
    }
    private record Semantic(DialogueAct act, ExpectedAnswerKind answerKind,
            EpistemicQueryKind queryKind, String subject, String predicate, String object,
            String target, String requestedAction, boolean inputQuality,
            boolean objectiveBiography, double confidence, String signal) { }
}
