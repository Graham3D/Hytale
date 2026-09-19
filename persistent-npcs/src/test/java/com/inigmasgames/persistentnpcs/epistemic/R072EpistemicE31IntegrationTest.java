package com.inigmasgames.persistentnpcs.epistemic;

import com.inigmasgames.persistentnpcs.conversation.AdaptiveReasoningDecision;
import com.inigmasgames.persistentnpcs.conversation.AdaptiveReasoningPolicy;
import com.inigmasgames.persistentnpcs.conversation.CognitiveContextPlan;
import com.inigmasgames.persistentnpcs.conversation.ConversationContextBuilder;
import com.inigmasgames.persistentnpcs.conversation.contract.ContractMessagePruner;
import com.inigmasgames.persistentnpcs.conversation.contract.TurnPlanCompiler;
import com.inigmasgames.persistentnpcs.llm.ChatMessage;
import com.inigmasgames.persistentnpcs.llm.LlmRequest;
import com.inigmasgames.persistentnpcs.orbis.CanonicalSpeechLedger;
import com.inigmasgames.persistentnpcs.orbis.ResponseId;
import com.inigmasgames.persistentnpcs.orbis.SpeechChunkId;
import com.inigmasgames.persistentnpcs.profile.NpcProfile;
import com.inigmasgames.persistentnpcs.voice.VocalState;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Gate A regression for the production E3 handoff, prompt budget and speech firewall. */
public final class R072EpistemicE31IntegrationTest {
    private R072EpistemicE31IntegrationTest() { }

    public static void main(String[] args) throws Exception {
        System.setProperty(EpistemicFeatureMode.PROPERTY, "AUTHORITATIVE");
        classificationFixtures();
        productionRouteAndPromptBudget();
        firewallAndCanonicalLedger();
        traceAndBudgetOrdering();
        pipelineLatency();
        System.out.println("R072 E3.1 production handoff tests passed.");
    }

    private static void classificationFixtures() {
        assert kind("Can you tell me a dry joke?") == EpistemicQueryKind.GENERAL_SOCIAL;
        assert kind("Why is the flame flickering?") == EpistemicQueryKind.OBJECTIVE_PROPERTY;
        assert kind("Is anything in here damaged?") == EpistemicQueryKind.OBJECTIVE_PROPERTY;
        assert kind("What am I holding?") == EpistemicQueryKind.CURRENT_PERCEPTION;
        assert kind("What are you doing?") == EpistemicQueryKind.NPC_SELF_STATE;
        assert kind("Do you like apples?") == EpistemicQueryKind.SUBJECTIVE_PREFERENCE;
        ConversationWorkspace workspace = new ConversationWorkspace();
        EpistemicContract held = EpistemicShadowAnalyzer.analyzeInitial(
                "What am I holding?", workspace);
        workspace.observeEvidence(packet(held, List.of(evidence("held", "CURRENT_PLAYER",
                "HELD_ITEM", "lantern"))), Instant.now());
        EpistemicContract flame = EpistemicShadowAnalyzer.analyzeInitial(
                "Is its flame flickering?", workspace);
        assert flame.queryPlan().queryKind().equals("OBJECTIVE_PROPERTY");
        assert flame.dialogueFrame().subjectKey().equals("OBJECT:LANTERN");
        assert flame.dialogueFrame().predicateKey().equals("PROPERTY:FLAME_FLICKERING");
        EpistemicContract damaged = EpistemicShadowAnalyzer.analyzeInitial(
                "Is it damaged?", workspace);
        assert damaged.dialogueFrame().subjectKey().equals("OBJECT:LANTERN");
        assert damaged.dialogueFrame().predicateKey().equals("PROPERTY:DAMAGED");
    }

    private static void productionRouteAndPromptBudget() {
        for (Case fixture : List.of(
                new Case("Can you tell me a dry joke?", Answerability.SUBJECTIVE, List.of()),
                new Case("What's my name?", Answerability.KNOWN,
                        List.of(evidence("name", "CURRENT_PLAYER", "NAME", "Graham"))),
                new Case("Why is the flame flickering?", Answerability.UNKNOWN, List.of()),
                new Case("Is anything in here damaged?", Answerability.UNKNOWN, List.of()),
                new Case("What am I holding?", Answerability.KNOWN,
                        List.of(evidence("held", "CURRENT_PLAYER", "HELD_ITEM", "lantern"))),
                new Case("What are you doing?", Answerability.KNOWN,
                        List.of(evidence("task", "CURRENT_NPC", "CURRENT_TASK",
                                "checking the forge"))),
                new Case("Do you like apples?", Answerability.SUBJECTIVE, List.of()),
                new Case("What did you mean by that?", Answerability.KNOWN,
                        List.of(evidence("prior", "PRIOR_NPC_CLAIM", "MEANING",
                                "I meant the lantern"))),
                new Case("Can you follow me?", Answerability.NEEDS_ACTION,
                        List.of(evidence("cap", "CURRENT_NPC", "ACTION_CAPABILITY",
                                "FOLLOW_PLAYER"))))) {
            EpistemicContract contract = authoritative(fixture.utterance(),
                    fixture.answerability(), fixture.evidence());
            AdaptiveReasoningDecision legacy = new AdaptiveReasoningDecision(
                    AdaptiveReasoningPolicy.DELIBERATIVE, List.of("LEGACY_WRONG_ROUTE"));
            AdaptiveReasoningDecision route = EpistemicProductionRoute.reasoning(contract, legacy);
            EpistemicQueryKind queryKind = kind(fixture.utterance());
            if (queryKind == EpistemicQueryKind.GENERAL_SOCIAL
                    || kind(fixture.utterance()) == EpistemicQueryKind.SUBJECTIVE_PREFERENCE) {
                assert route.policy() == AdaptiveReasoningPolicy.FAST_DIALOGUE : route;
            } else if (queryKind == EpistemicQueryKind.ACTION_REQUEST) {
                assert route.policy() == AdaptiveReasoningPolicy.DIRECT_ACTION : route;
            } else {
                assert route.policy() == AdaptiveReasoningPolicy.GROUNDED_DIALOGUE : route;
            }
            assert !route.policy().reasoningEnabled();
            CognitiveContextPlan context = EpistemicProductionRoute.context(contract,
                    CognitiveContextPlan.full("LEGACY"));
            assert !context.includes("MEMORIES") && !context.includes("BELIEFS");
            assert context.includes("ACTIONS") == (queryKind == EpistemicQueryKind.ACTION_REQUEST)
                    : fixture;
            assert context.includes("SEMANTIC_WORLD") == (queryKind
                    == EpistemicQueryKind.CURRENT_PERCEPTION
                    || queryKind == EpistemicQueryKind.OBJECTIVE_PROPERTY) : fixture;
            boolean deterministicAction = queryKind == EpistemicQueryKind.ACTION_REQUEST;
            var draft = TurnPlanCompiler.draft(context, route, deterministicAction,
                    false, true);
            LlmRequest inherited = new LlmRequest(UUID.randomUUID(), UUID.randomUUID(),
                    UUID.randomUUID(), List.of(new ChatMessage("system", "x".repeat(9_000)),
                            new ChatMessage("user", fixture.utterance())));
            NpcProfile mara = new NpcProfile(UUID.randomUUID(), "Mara", "blacksmith",
                    "curious, practical, dry-witted", "", "", "", "", List.of("apples"),
                    List.of(), List.of(), List.of(), 0);
            ConversationContextBuilder renderer = new ConversationContextBuilder(null, null, 0);
            LlmRequest finalPrompt = renderer.applyEpistemicContract(inherited, contract, mara,
                    "Mara: Prior delivered line.", false);
            finalPrompt = ContractMessagePruner.prune(finalPrompt, draft.contextProfile());
            var plan = TurnPlanCompiler.compile(UUID.randomUUID(), UUID.randomUUID(), 0, draft,
                    finalPrompt.messages(), null,
                    fixture.evidence().stream().map(EvidenceRef::stableId).toList(), contract);
            int characters = finalPrompt.messages().stream()
                    .mapToInt(message -> message.content().length()).sum();
            System.out.println("R072 prompt " + queryKind + " characters=" + characters
                    + " approximateTokens=" + ((characters + 3) / 4)
                    + " ceiling=" + draft.contextProfile().promptTokenCeiling());
            assert characters <= draft.contextProfile().promptTokenCeiling() * 4 - 96
                    : fixture + " promptCharacters=" + characters;
            assert plan.epistemicContract() == contract;
            assert finalPrompt.messages().getFirst().content().contains("EPISTEMIC ANSWER PLAN");
            assert !finalPrompt.messages().getFirst().content().contains("x".repeat(100));
        }
    }

    private static void firewallAndCanonicalLedger() {
        EpistemicClaimFirewall firewall = new EpistemicClaimFirewall();
        EpistemicContract unknown = authoritative("Why is the flame flickering?",
                Answerability.UNKNOWN, List.of());
        var flame = firewall.validate("The flame is flickering because the oil is low.",
                unknown, false);
        assert flame.valid() && flame.dialogue().equals("I don't know that.") : flame;

        EpistemicContract held = authoritative("What am I holding?", Answerability.KNOWN,
                List.of(evidence("held", "CURRENT_PLAYER", "HELD_ITEM", "lantern")));
        var result = firewall.validate(
                "You're holding a lantern. It is damaged.", held, false);
        assert result.valid() && result.dialogue().equals("You're holding a lantern.") : result;

        EpistemicContract social = authoritative("Can you tell me a dry joe?",
                Answerability.SUBJECTIVE, List.of());
        String quip = "Dry Joe? Only if he’s got a wrench in his pocket and a storm brewing.";
        var conversational = firewall.validate(quip, social, false);
        assert conversational.valid() && conversational.dialogue().equals(quip)
                : conversational;
        CanonicalSpeechLedger ledger = new CanonicalSpeechLedger(ResponseId.create());
        ledger.append(SpeechChunkId.create(), 0, result.dialogue(), VocalState.infer("calm"));
        assert ledger.canonicalText().equals(result.dialogue());
    }

    private static void traceAndBudgetOrdering() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/inigmasgames/"
                + "persistentnpcs/conversation/ConversationService.java"));
        int trace = source.indexOf("\"epistemic-contract\"");
        int compile = source.indexOf("TurnPlanCompiler.compile(responseId");
        int render = source.indexOf("contextBuilder.applyEpistemicContract(baseRequest");
        int prune = source.indexOf("ContractMessagePruner.prune(baseRequest");
        assert trace >= 0 && trace < compile : "contract trace must precede fallible compile";
        assert render >= 0 && render < prune : "final prompt must exist before final pruning";
        assert source.contains("EpistemicProductionRoute.reasoning(")
                && source.contains("completedEpistemicShadow, legacyReasoning");
        assert source.contains("EpistemicProductionRoute.context(");
    }

    private static void pipelineLatency() {
        ArrayList<Long> samples = new ArrayList<>();
        for (int index = 0; index < 1_000; index++) {
            long started = System.nanoTime();
            String utterance = index % 2 == 0 ? "What am I holding?"
                    : "Why is the flame flickering?";
            EpistemicContract contract = authoritative(utterance,
                    index % 2 == 0 ? Answerability.KNOWN : Answerability.UNKNOWN,
                    index % 2 == 0 ? List.of(evidence("held", "CURRENT_PLAYER",
                            "HELD_ITEM", "lantern")) : List.of());
            var route = EpistemicProductionRoute.reasoning(contract,
                    new AdaptiveReasoningDecision(AdaptiveReasoningPolicy.DELIBERATIVE,
                            List.of()));
            var result = new EpistemicClaimFirewall().validate(index % 2 == 0
                    ? "You're holding a lantern." : "The oil is low.", contract, false);
            assert !route.policy().reasoningEnabled() && result.valid();
            samples.add((System.nanoTime() - started) / 1_000L);
        }
        samples.sort(Comparator.naturalOrder());
        long p95 = samples.get((int) (samples.size() * .95));
        assert p95 < 40_000 : "E0-E3.1 pipeline p95Micros=" + p95;
        System.out.println("R072 E0-E3.1 pipeline p95Micros=" + p95);
    }

    private static EpistemicQueryKind kind(String utterance) {
        EpistemicContract result = EpistemicShadowAnalyzer.analyzeInitial(utterance,
                new ConversationWorkspace());
        return EpistemicQueryKind.valueOf(result.queryPlan().queryKind());
    }

    private static EpistemicContract authoritative(String utterance,
            Answerability answerability, List<EvidenceRef> evidence) {
        EpistemicContract base = EpistemicShadowAnalyzer.analyzeInitial(utterance,
                new ConversationWorkspace());
        EvidencePacket packet = packet(base, evidence);
        AnswerPlan answer = new AnswerPlan(AnswerPlan.SCHEMA_VERSION,
                base.dialogueFrame().expectedAnswer().name(),
                evidence.stream().map(EvidenceRef::compactProposition).toList(), evidence,
                answerability.name(), 2, answerability == Answerability.SUBJECTIVE ? 0 : 1,
                Set.of(), Set.of(), "E3_AUTHORITATIVE", "answer directly", List.of(),
                kind(utterance) == EpistemicQueryKind.ACTION_REQUEST ? "FOLLOW_PLAYER" : "",
                List.of());
        return new EpistemicContract(base.schemaVersion(), EpistemicFeatureMode.AUTHORITATIVE,
                base.dialogueFrame(), base.queryPlan(), packet, answerability, answer,
                base.claimPolicy(), base.budget(), List.of("E3.1_TEST"),
                base.planningMicros(), Instant.now());
    }

    private static EvidencePacket packet(EpistemicContract base, List<EvidenceRef> evidence) {
        return new EvidencePacket(EvidencePacket.SCHEMA_VERSION,
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null,
                base.queryPlan().queryKind(), base.dialogueFrame().subjectKey(),
                base.dialogueFrame().predicateKey(), evidence, List.of(), List.of(), List.of(),
                evidence.isEmpty() ? EvidenceSufficiency.NONE : EvidenceSufficiency.SUFFICIENT,
                List.of(), List.of(), 0, List.of(),
                evidence.stream().map(EvidenceRef::sourceKind).toList(), 10, 1, false);
    }

    private static EvidenceRef evidence(String id, String subject, String predicate,
            String object) {
        return new EvidenceRef(EvidenceRef.SCHEMA_VERSION, id,
                EvidenceSourceKind.DIRECT_OBSERVATION, EpistemicStatus.KNOWN, 1, true,
                subject + " " + predicate + " " + object, subject, predicate, object,
                Instant.now(), "CURRENT", "SERVER", true, true, "world", "CURRENT");
    }

    private record Case(String utterance, Answerability answerability,
            List<EvidenceRef> evidence) { }
}
