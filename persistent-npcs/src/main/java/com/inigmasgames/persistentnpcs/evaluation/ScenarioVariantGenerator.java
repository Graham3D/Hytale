package com.inigmasgames.persistentnpcs.evaluation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Deterministic neighboring variants; no model-generated expectations. */
public final class ScenarioVariantGenerator {
    public enum VariantKind { PARAPHRASE, ENTITY, TEMPORAL, REFERENT, NEGATIVE_CONTROL }

    public List<Variant> identityRelationshipVariants() {
        return List.of(
                new Variant(VariantKind.PARAPHRASE, "Tell me who you are.", false),
                new Variant(VariantKind.PARAPHRASE, "Introduce yourself.", false),
                new Variant(VariantKind.ENTITY, "Who is Mara?", true),
                new Variant(VariantKind.REFERENT, "Are you related to me?", false),
                new Variant(VariantKind.NEGATIVE_CONTROL, "Are you Mara's grandfather?", true));
    }

    public List<EvaluationContracts.ConversationScenario> expand(
            EvaluationContracts.ConversationScenario source, List<Variant> variants) {
        if (source.turns().size() != 1) throw new IllegalArgumentException(
                "Variant expansion requires a one-turn source scenario");
        ArrayList<EvaluationContracts.ConversationScenario> values = new ArrayList<>();
        for (int index = 0; index < variants.size(); index++) {
            Variant variant = variants.get(index);
            var original = source.turns().getFirst();
            var turn = new EvaluationContracts.ScenarioTurn(0, original.speaker(),
                    original.audience(), variant.utterance(), original.ingress(),
                    original.pacing(), original.expected(), Map.of("variantKind",
                            variant.kind().name()));
            values.add(new EvaluationContracts.ConversationScenario(source.id() + "-v" + index,
                    source.description(), source.actors(), source.world(), source.cognition(),
                    List.of(turn), java.util.stream.Stream.concat(source.coverageTags().stream(),
                            java.util.stream.Stream.of("VARIANT_" + variant.kind())).collect(
                                    java.util.stream.Collectors.toUnmodifiableSet()),
                    EvaluationContracts.ResetPolicy.RESET_EACH_SCENARIO));
        }
        return List.copyOf(values);
    }

    public record Variant(VariantKind kind, String utterance,
            boolean authoredRelationshipMayBeUsed) { }
}
