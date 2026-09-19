package com.inigmasgames.persistentnpcs.evaluation;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Read-only importer that projects existing trace JSONL into boundary evidence. */
public final class TraceImportService {
    public ImportedTurn analyzeTurn(Path trace, String playerText) {
        if (trace == null || !Files.isRegularFile(trace)) throw new IllegalArgumentException(
                "Trace file is required");
        String responseId = null;
        ArrayList<JsonObject> matched = new ArrayList<>();
        try {
            for (String line : Files.readAllLines(trace, StandardCharsets.UTF_8)) {
                JsonObject value;
                try { value = JsonParser.parseString(line).getAsJsonObject(); }
                catch (RuntimeException malformed) { continue; }
                if (responseId == null && playerText.equals(text(value, "playerText"))) {
                    responseId = text(value, "responseId");
                }
                if (responseId != null && responseId.equals(text(value, "responseId"))) {
                    matched.add(value);
                }
            }
        } catch (IOException failure) {
            throw new IllegalStateException("Could not import trace", failure);
        }
        if (responseId == null) throw new IllegalArgumentException(
                "Turn not found in trace: " + playerText);
        String query = "", answerability = "", planEvidence = "", dispatchSections = "";
        String raw = "", canonical = "";
        for (JsonObject value : matched) {
            String event = text(value, "event");
            String type = text(value, "orbisType");
            if (type.equals("TURN_PLAN_COMPILED")) {
                query = text(value, "epistemicQueryKind");
                answerability = text(value, "epistemicAnswerability");
                planEvidence = text(value, "evidenceIds");
            } else if (type.equals("LLM_DISPATCHED")) {
                dispatchSections = text(value, "contextSections");
            } else if (event.equals("MODEL_OUTPUT")) raw = text(value, "rawModelOutput");
            else if (event.equals("CANONICAL_RESPONSE")) canonical = text(value,
                    "canonicalResponse");
        }
        boolean retrievalMissing = planEvidence.isBlank() || planEvidence.equals("[]");
        boolean contextMissing = !dispatchSections.contains("MEMORIES")
                && (query.equals("EPISODIC_RECALL") || !planEvidence.isBlank());
        ArrayList<EvaluationContracts.FailureClass> failures = new ArrayList<>();
        if (retrievalMissing) failures.add(EvaluationContracts.FailureClass.RETRIEVAL);
        if (contextMissing) failures.add(EvaluationContracts.FailureClass.CONTEXT_RENDER);
        if (!raw.equals(canonical)) failures.add(EvaluationContracts.FailureClass.CLAIM_AUTHORITY);
        return new ImportedTurn(responseId, playerText, query, answerability, planEvidence,
                dispatchSections, raw, canonical, List.copyOf(failures));
    }

    private static String text(JsonObject value, String key) {
        return value.has(key) && !value.get(key).isJsonNull()
                ? value.get(key).getAsString() : "";
    }

    public record ImportedTurn(String responseId, String playerText, String queryKind,
            String answerability, String planEvidence, String dispatchContextSections,
            String rawModelOutput, String canonicalResponse,
            List<EvaluationContracts.FailureClass> failures) { }
}
