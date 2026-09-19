package com.inigmasgames.persistentnpcs.epistemic;

import com.inigmasgames.persistentnpcs.conversation.CognitiveContextPlan;
import com.inigmasgames.persistentnpcs.conversation.ConversationContextBuilder;
import com.inigmasgames.persistentnpcs.llm.ChatMessage;
import com.inigmasgames.persistentnpcs.llm.LlmRequest;
import com.inigmasgames.persistentnpcs.orbis.CanonicalSpeechLedger;
import com.inigmasgames.persistentnpcs.orbis.ResponseId;
import com.inigmasgames.persistentnpcs.orbis.SpeechChunkId;
import com.inigmasgames.persistentnpcs.voice.VocalState;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** E3 authoritative answer-plan and atomic-claim release regressions. */
public final class R071EpistemicE3Test {
    private R071EpistemicE3Test() { }

    public static void main(String[] args) {
        System.setProperty(EpistemicFeatureMode.PROPERTY, "AUTHORITATIVE");
        directAnswerAndUnsupportedClauseRepair();
        typedObjectiveClaimsFailClosed();
        subjectiveHypotheticalAndMetaphorRemainExpressive();
        unknownConflictAndActionAuthority();
        canonicalLedgerReceivesOnlyValidatedSpeech();
        promptRenderingIsBounded();
        authoritativeModeAndLatency();
        System.out.println("R071 E3 authoritative claim-firewall tests passed.");
    }

    private static void directAnswerAndUnsupportedClauseRepair() {
        EpistemicContract identity = contract(Answerability.KNOWN, "NAME", 1,
                List.of(evidence("name", "CURRENT_PLAYER", "NAME", "Graham")), List.of());
        EpistemicClaimFirewall gate = new EpistemicClaimFirewall();
        var natural = gate.validate("Graham. Of course I remember.", identity, false);
        assert natural.valid() && natural.dialogue().equals("Graham. Of course I remember.")
                : natural;
        var missingDirect = gate.validate("You're holding a lantern.", identity, false);
        assert missingDirect.repaired() && missingDirect.dialogue().equals("Graham.")
                : missingDirect;
        assert missingDirect.claims().stream().anyMatch(value -> value.releasable()
                && value.claim().objectValue().equals("Graham")) : missingDirect;

        EpistemicContract held = contract(Answerability.KNOWN, "HELD_ITEM", 1,
                List.of(evidence("held", "CURRENT_PLAYER", "HELD_ITEM", "lantern")), List.of());
        var mixed = gate.validate("You're holding a lantern, and the lantern is flickering.",
                held, false);
        assert mixed.dialogue().equals("You're holding a lantern.") : mixed;
        assert mixed.claims().stream().anyMatch(value -> !value.releasable());
    }

    private static void typedObjectiveClaimsFailClosed() {
        EpistemicContract identity = contract(Answerability.KNOWN, "NAME", 1,
                List.of(evidence("name", "CURRENT_PLAYER", "NAME", "Graham")), List.of());
        EpistemicClaimFirewall gate = new EpistemicClaimFirewall();
        List<String> adversarial = List.of(
                "Graham. Elara is your sister.",
                "Graham. I visited the north mill last night.",
                "Graham. I own a silver crown.",
                "Graham. The gate is open.",
                "Graham. I already repaired your sword.");
        for (String fake : adversarial) {
            var result = gate.validate(fake, identity, false);
            assert result.dialogue().equals("Graham.") : fake + " -> " + result;
            assert result.claims().stream().anyMatch(value -> !value.releasable());
        }
    }

    private static void subjectiveHypotheticalAndMetaphorRemainExpressive() {
        EpistemicContract subjective = contract(Answerability.SUBJECTIVE, "SUBJECTIVE", 0,
                List.of(), List.of());
        EpistemicClaimFirewall gate = new EpistemicClaimFirewall();
        var embedded = gate.validate(
                "I hate spiders because my brother was killed by one.", subjective, false);
        assert embedded.dialogue().equals("I hate spiders.") : embedded;
        assert embedded.claims().stream().anyMatch(value -> !value.releasable());
        var hypothetical = gate.validate(
                "If the lantern were flickering, I would notice.", subjective, false);
        assert !hypothetical.repaired() : hypothetical;
        var metaphor = gate.validate("That idea is a storm in a teacup.", subjective, false);
        assert !metaphor.repaired() : metaphor;
    }

    private static void unknownConflictAndActionAuthority() {
        EpistemicClaimFirewall gate = new EpistemicClaimFirewall();
        EpistemicContract unknown = contract(Answerability.UNKNOWN, "UNKNOWN", 0,
                List.of(), List.of());
        var invented = gate.validate("The mill is on fire.", unknown, false);
        assert invented.dialogue().equals("I don't know that.") : invented;

        EpistemicContract conflict = contract(Answerability.CONFLICTED, "CONFLICT", 1,
                List.of(evidence("new", "CURRENT_PLAYER", "NAME", "Greg")),
                List.of(evidence("old", "CURRENT_PLAYER", "NAME", "Graham")));
        var conflicted = gate.validate("Greg.", conflict, false);
        assert conflicted.dialogue().toLowerCase().contains("conflicting") : conflicted;

        EpistemicContract action = contract(Answerability.NEEDS_ACTION, "ACTION", 0,
                List.of(evidence("cap", "CURRENT_NPC", "ACTION_CAPABILITY",
                        "FOLLOW_PLAYER")), List.of());
        var promise = gate.validate("I'll follow you.", action, false);
        assert promise.repaired() && promise.dialogue().equals("I can follow player.") : promise;
        var committed = gate.validate("I'll follow you.", action, true);
        assert !committed.repaired() : committed;
        var unrelatedResult = gate.validate("I sold the sword.", action,
                "FOLLOW_PLAYER completed", true);
        assert unrelatedResult.repaired()
                && unrelatedResult.dialogue().equals("I can follow player.")
                : unrelatedResult;
    }

    private static void canonicalLedgerReceivesOnlyValidatedSpeech() {
        EpistemicContract held = contract(Answerability.KNOWN, "HELD_ITEM", 1,
                List.of(evidence("held", "CURRENT_PLAYER", "HELD_ITEM", "lantern")), List.of());
        var result = new EpistemicClaimFirewall().validate(
                "You're holding a lantern. The flame is flickering.", held, false);
        assert result.dialogue().equals("You're holding a lantern.") : result;
        CanonicalSpeechLedger ledger = new CanonicalSpeechLedger(ResponseId.create());
        ledger.append(SpeechChunkId.create(), 0, result.dialogue(), VocalState.infer("calm"));
        assert ledger.canonicalText().equals(result.dialogue());
        assert !ledger.canonicalText().contains("flickering");
    }

    private static void authoritativeModeAndLatency() {
        EpistemicContract mode = EpistemicShadowAnalyzer.analyze("What's my name?",
                CognitiveContextPlan.full("E3_TEST"), null);
        assert mode.mode() == EpistemicFeatureMode.AUTHORITATIVE : mode.mode();
        EpistemicContract held = contract(Answerability.KNOWN, "HELD_ITEM", 1,
                List.of(evidence("held", "CURRENT_PLAYER", "HELD_ITEM", "lantern")), List.of());
        EpistemicClaimFirewall gate = new EpistemicClaimFirewall();
        ArrayList<Long> timings = new ArrayList<>();
        for (int index = 0; index < 2_000; index++) {
            var result = gate.validate(index % 2 == 0
                    ? "You're holding a lantern." : "You're holding a lantern. Thanks.",
                    held, false);
            assert result.valid();
            timings.add(result.totalMicros());
        }
        timings.sort(Comparator.naturalOrder());
        long p95 = timings.get((int) (timings.size() * .95));
        assert p95 < 10_000 : "phrase validation p95Micros=" + p95;
        assert p95 + held.planningMicros() < 40_000
                : "E0-E3 p95Micros=" + (p95 + held.planningMicros());
        System.out.println("R071 E3 validation p95Micros=" + p95);
    }

    private static void promptRenderingIsBounded() {
        EpistemicContract held = contract(Answerability.KNOWN, "HELD_ITEM", 1,
                List.of(evidence("held", "CURRENT_PLAYER", "HELD_ITEM", "lantern")), List.of());
        ConversationContextBuilder renderer = new ConversationContextBuilder(null, null, 0);
        LlmRequest base = new LlmRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                List.of(new ChatMessage("system", "Character style."),
                        new ChatMessage("user", "What am I holding?")));
        ArrayList<Long> samples = new ArrayList<>();
        for (int index = 0; index < 1_000; index++) {
            long started = System.nanoTime();
            LlmRequest rendered = renderer.applyEpistemicContract(base, held);
            samples.add((System.nanoTime() - started) / 1_000L);
            String prompt = rendered.messages().getFirst().content();
            assert prompt.contains("ANSWER PLAN") && prompt.contains("HELD_ITEM");
            assert !prompt.contains("RELEVANT MEMORIES");
        }
        samples.sort(Comparator.naturalOrder());
        long p95 = samples.get((int) (samples.size() * .95));
        assert p95 < 5_000 : "AnswerPlan rendering p95Micros=" + p95;
        System.out.println("R071 AnswerPlan rendering p95Micros=" + p95);
    }

    private static EpistemicContract contract(Answerability answerability, String predicate,
            int maxObjectiveClaims, List<EvidenceRef> support, List<EvidenceRef> contradict) {
        EpistemicContract base = EpistemicShadowAnalyzer.analyze("What's my name?",
                CognitiveContextPlan.full("E3_TEST"), null);
        EvidencePacket packet = new EvidencePacket(EvidencePacket.SCHEMA_VERSION,
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null,
                predicate, "CURRENT_PLAYER", predicate, support, contradict, List.of(),
                List.of(), contradict.isEmpty() ? (support.isEmpty()
                        ? EvidenceSufficiency.NONE : EvidenceSufficiency.SUFFICIENT)
                        : EvidenceSufficiency.CONFLICTED,
                List.of(), List.of(), 0, List.of(), support.stream()
                        .map(EvidenceRef::sourceKind).distinct().toList(), 32, 1, false);
        AnswerPlan answer = new AnswerPlan(AnswerPlan.SCHEMA_VERSION, predicate,
                support.stream().map(EvidenceRef::compactProposition).toList(), support,
                answerability.name(), 3, maxObjectiveClaims, Set.of(), Set.of(),
                "E3_AUTHORITATIVE", "answer directly", List.of(),
                answerability == Answerability.NEEDS_ACTION ? "FOLLOW_PLAYER" : "", List.of());
        return new EpistemicContract(EpistemicContract.SCHEMA_VERSION,
                EpistemicFeatureMode.AUTHORITATIVE, base.dialogueFrame(), base.queryPlan(),
                packet, answerability, answer, base.claimPolicy(), base.budget(),
                List.of("E3_TEST"), base.planningMicros(), Instant.now());
    }

    private static EvidenceRef evidence(String id, String subject, String predicate,
            String object) {
        return new EvidenceRef(EvidenceRef.SCHEMA_VERSION, id,
                predicate.equals("ACTION_CAPABILITY") ? EvidenceSourceKind.ACTION_CAPABILITY
                        : EvidenceSourceKind.DIRECT_OBSERVATION,
                EpistemicStatus.KNOWN, 1, true,
                subject + " " + predicate + " " + object, subject, predicate, object,
                Instant.now(), "CURRENT", "SERVER", true, true, "world", "CURRENT");
    }
}
