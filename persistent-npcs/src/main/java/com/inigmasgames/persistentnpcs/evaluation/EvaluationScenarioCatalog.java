package com.inigmasgames.persistentnpcs.evaluation;

import com.inigmasgames.persistentnpcs.epistemic.Answerability;
import com.inigmasgames.persistentnpcs.epistemic.ConversationWorkspace;
import com.inigmasgames.persistentnpcs.epistemic.EpistemicShadowAnalyzer;
import com.inigmasgames.persistentnpcs.profile.ProfileRepository;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Project-owned semantic scenarios; provider prose is never encoded as an expectation. */
public final class EvaluationScenarioCatalog {
    private EvaluationScenarioCatalog() { }

    public static EvaluationContracts.ConversationScenario lycanderLiveSmoke(Path root) {
        return singleActor(root, "Lycander", "h2-lycander-live",
                List.of("Hello, Lycander.", "Who are you?", "What matters to you?"),
                Set.of("H2", "IDENTITY", "PREFERENCE"));
    }

    public static EvaluationContracts.ConversationScenario singleActor(Path root,
            String name, String id, List<String> utterances, Set<String> tags) {
        ProfileRepository profiles = new ProfileRepository(root);
        var profile = profiles.load(name);
        String safe = ProfileRepository.sanitizeProfileName(name).toLowerCase(
                java.util.Locale.ROOT);
        Path source = root.resolve("profiles").resolve(safe).resolve(safe + ".json");
        var actor = new EvaluationContracts.ScenarioActor(profile.id(), profile.name(), source);
        UUID player = UUID.nameUUIDFromBytes((id + ":player").getBytes(
                java.nio.charset.StandardCharsets.UTF_8));
        ArrayList<EvaluationContracts.ScenarioTurn> turns = new ArrayList<>();
        for (int index = 0; index < utterances.size(); index++) {
            String text = utterances.get(index);
            var expected = expected(name, text);
            turns.add(new EvaluationContracts.ScenarioTurn(index, player,
                    List.of(profile.id()), text,
                    EvaluationContracts.IngressKind.AUTHORITATIVE_EVALUATION_TEXT,
                    EvaluationContracts.PacingPolicy.WAIT_FOR_TERMINAL, expected, Map.of()));
        }
        return new EvaluationContracts.ConversationScenario(id,
                "Production-parity headless conversation with " + name, List.of(actor),
                new EvaluationContracts.ScenarioWorldState("headless-evaluation", 0, 64, 0,
                        Map.of("location", "quiet evaluation room"), Set.of(name), Map.of()),
                new EvaluationContracts.ScenarioCognitiveState(List.of(), List.of(),
                        List.of(), List.of(), List.of(), List.of()), turns, tags,
                EvaluationContracts.ResetPolicy.RESET_EACH_SCENARIO);
    }

    private static EvaluationContracts.ExpectedTurnContract expected(String name,
            String utterance) {
        String text = utterance.toLowerCase(java.util.Locale.ROOT);
        String query = "";
        Answerability answerability = null;
        String action = "";
        List<EvaluationContracts.ExpectedProposition> propositions = List.of();
        Set<String> forbidden = Set.of("UNSUPPORTED_OBJECTIVE_FACT");
        var semanticContract = EpistemicShadowAnalyzer.analyzeInitial(utterance,
                new ConversationWorkspace());
        if (text.contains("who are you")) {
            query = "UNRESOLVED";
            propositions = List.of(proposition("CURRENT_NPC", "IDENTITY", name,
                    "OBJECTIVE_FACT"));
            forbidden = Set.of("your grandfather", "your father", "your mother");
        } else if (semanticContract != null && semanticContract.queryPlan().queryKind()
                .equals("CORRECTION")) {
            query = "CORRECTION";
            answerability = Answerability.KNOWN;
            var frame = semanticContract.dialogueFrame();
            String corrected = frame.objectKey()
                    .replaceFirst("^(?:PERSON_NAME|CORRECTED_VALUE):", "")
                    .replace('_', ' ');
            propositions = List.of(proposition(frame.subjectKey(), frame.predicateKey(),
                    corrected, ""));
        } else if (text.contains("golden crown")) {
            query = "EPISODIC_RECALL";
            propositions = List.of(proposition("CURRENT_NPC", "UNCERTAINTY", "know|remember",
                    "SUBJECTIVE_OPINION"));
            forbidden = Set.of("silver key", "under a large rock");
        } else if (text.contains("what did i hide") || text.contains("where did i put")) {
            query = "EPISODIC_RECALL";
            propositions = List.of(proposition("CURRENT_PLAYER", "PAST_EVENT",
                    "silver key under a large rock", "OBJECTIVE_FACT"));
            forbidden = Set.of("four jars", "in your pocket");
        } else if (text.contains("dragon behind the moon")) {
            query = "OBJECTIVE_PROPERTY";
            answerability = Answerability.UNKNOWN;
            propositions = List.of(proposition("CURRENT_NPC", "UNCERTAINTY",
                    "know|certain|seen", ""));
            forbidden = Set.of("dragon behind the moon is black");
        } else if (text.contains("what am i holding") || text.contains("holding anything")
                || text.contains("in my hand") || text.contains("see what i'm holding")) {
            query = "CURRENT_PERCEPTION";
            answerability = Answerability.KNOWN;
            propositions = List.of(proposition("CURRENT_PLAYER", "HELD_ITEM", "nothing",
                    "OBJECTIVE_FACT"));
            forbidden = Set.of("you're holding a lantern", "you are holding a dagger");
        } else if (text.contains("follow me") || text.contains("come with me")) {
            query = "ACTION_REQUEST";
            answerability = Answerability.NEEDS_ACTION;
            action = "FOLLOW_PLAYER";
            propositions = List.of(proposition("CURRENT_NPC", "ACTION", "follow",
                    "OBJECTIVE_FACT"));
        } else if (text.matches(".*\\b(?:put|place|move|take|bring|carry|drop|give|set|leave)\\s+"
                + "(?:it|this|that)\\b.*")) {
            query = "ACTION_REQUEST";
            answerability = Answerability.AMBIGUOUS;
            propositions = List.of(proposition("", "CLARIFICATION",
                    text.matches(".*\\b(?:here|there)\\b.*") ? "where" : "which|what",
                    "QUESTION"));
        } else if (text.contains("what do you want") || text.contains("what is your goal")
                || text.contains("what are you trying to do")) {
            query = "SUBJECTIVE_PREFERENCE";
            answerability = Answerability.SUBJECTIVE;
            propositions = List.of(proposition("CURRENT_NPC", "DESIRE", "want",
                    "DESIRE"));
        } else if (text.contains("how do you feel") || text.contains("are you okay")
                || text.contains("are you happy")) {
            query = "SUBJECTIVE_PREFERENCE";
            answerability = Answerability.SUBJECTIVE;
            propositions = List.of(proposition("CURRENT_NPC", "EMOTION", "feel",
                    "EMOTION"));
        } else if (text.contains("what are you doing")
                || text.contains("what are you working on")) {
            query = "NPC_SELF_STATE";
            answerability = Answerability.KNOWN;
            propositions = List.of(new EvaluationContracts.ExpectedProposition(
                    "CURRENT_NPC", "CURRENT_TASK", "", "OBJECTIVE_FACT", "CURRENT",
                    Set.of("SELF_STATE")));
        } else if (text.contains("what's my name")) {
            query = "IDENTITY_RECALL";
            propositions = List.of(proposition("CURRENT_PLAYER", "NAME", "Graham",
                    "OBJECTIVE_FACT"));
        } else if (text.contains("i hid a silver key")) {
            query = "UNRESOLVED";
            forbidden = Set.of("I saw you hide", "I remember seeing");
        }
        return new EvaluationContracts.ExpectedTurnContract("RESPOND", query,
                Set.of(), Set.of(), answerability, propositions, forbidden, action,
                EvaluationContracts.ExpectedStateDelta.none(), Set.of("PROFILE"), Set.of(),
                30_000);
    }

    private static EvaluationContracts.ExpectedProposition proposition(String subject,
            String predicate, String value, String mode) {
        return new EvaluationContracts.ExpectedProposition(subject, predicate, value, mode,
                "CURRENT_TURN", Set.of());
    }

    public static EvaluationContracts.ConversationScenario gateA(Path root, String name,
            int repetitions) {
        List<String> probes = List.of("Who are you?", "My name is Graham, not Grant.",
                "I hid a silver key under a large rock.",
                "What did I hide, and where did I hide it?",
                "What color is the dragon behind the moon?", "What am I holding?",
                "Follow me.", "Put it there.", "What do you want?", "How do you feel?");
        ArrayList<String> turns = new ArrayList<>();
        for (int index = 0; index < repetitions; index++) turns.add(probes.get(
                index % probes.size()));
        return singleActor(root, name, "gate-a-" + name.toLowerCase(java.util.Locale.ROOT),
                turns, Set.of("GATE_A", "IDENTITY", "CORRECTION", "RECALL", "UNKNOWN",
                        "PERCEPTION", "ACTION", "CLARIFICATION", "SELF_STATE"));
    }

    public static EvaluationContracts.ConversationScenario behaviorHardening(Path root,
            String name) {
        return singleActor(root, name, "behavior-hardening-" + name.toLowerCase(
                        java.util.Locale.ROOT),
                List.of("What am I holding?", "Am I holding anything?",
                        "What is in my hand?", "Put it there.", "Move that over here.",
                        "Take this.", "Drop it there.", "What do you want?",
                        "What is your goal?", "What are you trying to do?",
                        "How do you feel?", "Are you okay?", "Are you happy?"),
                Set.of("GATE_A_REPAIR", "PERCEPTION", "CLARIFICATION", "SELF_STATE"));
    }

    /** Focused live probe for the remaining strict Gate-B correction/uncertainty boundaries. */
    public static EvaluationContracts.ConversationScenario gateBCleanup(Path root,
            String name) {
        return singleActor(root, name, "gate-b-cleanup-" + name.toLowerCase(
                        java.util.Locale.ROOT),
                List.of("My name is Graham, not Grant.",
                        "My name is Daniel, not David.",
                        "I live in Oakvale, not Riverbend.",
                        "The key is silver, not gold.",
                        "My name is Daniel, not David.",
                        "What color is the dragon behind the moon?"),
                Set.of("GATE_B_REPAIR", "CORRECTION", "UNKNOWN"));
    }

    /** Repeated live probe for intermittent provider/stream/recovery behavior. */
    public static EvaluationContracts.ConversationScenario desireStress(Path root,
            String name, int repetitions) {
        ArrayList<String> turns = new ArrayList<>();
        for (int index = 0; index < repetitions; index++) turns.add("What do you want?");
        return singleActor(root, name, "desire-stress-" + name.toLowerCase(
                        java.util.Locale.ROOT), turns,
                Set.of("GATE_B_REPAIR", "SUBJECTIVE_PREFERENCE", "PROVIDER_RECOVERY"));
    }

    /** Builds real turns corresponding to the campaign planner's declared capabilities. */
    public static EvaluationContracts.ConversationScenario campaign(Path root, String name,
            List<AutonomousCampaignPlanner.Probe> probes) {
        List<String> utterances = probes.stream().map(EvaluationScenarioCatalog::utterance)
                .toList();
        return singleActor(root, name, "campaign-" + name.toLowerCase(
                java.util.Locale.ROOT), utterances,
                Set.of("H6", "DETERMINISTIC", "CAPABILITY_COVERAGE"));
    }

    private static String utterance(AutonomousCampaignPlanner.Probe probe) {
        return switch (probe.capability()) {
            case IDENTITY -> "Who are you?";
            case MEMORY -> "I hid a silver key under a large rock.";
            case RECALL -> probe.mutation().equals("ENTITY_SWAP")
                    ? "Where did I put the golden crown?"
                    : probe.mutation().equals("PARAPHRASE")
                            ? "Where did I put the silver key?"
                            : "What did I hide, and where did I hide it?";
            case PERCEPTION -> probe.mutation().equals("PARAPHRASE")
                    ? "Can you see what I'm holding?" : "What am I holding?";
            case SELF_STATE -> probe.mutation().equals("PARAPHRASE")
                    ? "What are you working on?" : "What are you doing?";
            case ACTION -> probe.mutation().equals("PARAPHRASE")
                    ? "Come with me." : "Follow me.";
            case CORRECTION -> "My name is Graham, not Grant.";
            case UNCERTAINTY -> "What color is the dragon behind the moon?";
            case SOCIAL_COGNITION -> "How do you feel?";
            case PERSISTENCE -> "What's my name?";
        };
    }

    public static EvaluationContracts.ConversationScenario maraLycanderScene(Path root) {
        ProfileRepository profiles = new ProfileRepository(root);
        var mara = profiles.load("Mara"); var lycander = profiles.load("Lycander");
        var actors = List.of(actor(root, mara.name(), mara.id()),
                actor(root, lycander.name(), lycander.id()));
        var seed = new EvaluationContracts.ScenarioTurn(0, mara.id(), List.of(lycander.id()),
                "Good morning, Lycander.", EvaluationContracts.IngressKind
                        .AUTHORITATIVE_EVALUATION_TEXT,
                EvaluationContracts.PacingPolicy.WAIT_FOR_TERMINAL,
                EvaluationContracts.ExpectedTurnContract.openSocial(), Map.of());
        return new EvaluationContracts.ConversationScenario("h8-mara-lycander",
                "Bounded two-NPC production-parity headless scene", actors,
                new EvaluationContracts.ScenarioWorldState("headless-evaluation", 0, 64, 0,
                        Map.of("location", "Sandsdeep forge"),
                        Set.of("Mara", "Lycander"), Map.of()),
                new EvaluationContracts.ScenarioCognitiveState(List.of(), List.of(),
                        List.of(), List.of(), List.of(), List.of()), List.of(seed),
                Set.of("H8", "MULTI_AGENT", "PROVENANCE", "FLOOR"),
                EvaluationContracts.ResetPolicy.RESET_EACH_SCENARIO);
    }

    private static EvaluationContracts.ScenarioActor actor(Path root, String name, UUID id) {
        String safe = name.toLowerCase(java.util.Locale.ROOT);
        return new EvaluationContracts.ScenarioActor(id, name,
                root.resolve("profiles").resolve(safe).resolve(safe + ".json"));
    }
}
