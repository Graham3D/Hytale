package com.inigmasgames.persistentnpcs.training.curation;

import com.inigmasgames.persistentnpcs.training.curation.CurationContracts.CurationRequest;
import com.inigmasgames.persistentnpcs.training.curation.CurationContracts.SourceKind;
import com.inigmasgames.persistentnpcs.training.registry.CanonicalJson;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Fail-closed, offline privacy gate. It never exports or resolves local paths. */
public record CurationPrivacyPolicy(int schemaVersion, String policyId,
        boolean realPlayerCorpusApproved, boolean permitStablePlayerIdentifiers,
        boolean permitRawAudio, String decisionBasis, Instant reviewedAt) {
    public static final int SCHEMA_VERSION = 1;
    private static final Pattern WINDOWS_USER_PATH = Pattern.compile(
            "(?i)[a-z]:[/\\\\]users[/\\\\][^/\\\\\\s]+(?:[/\\\\][^\\s]+)*");
    private static final Pattern SECRET = Pattern.compile(
            "(?i)(?:api[_-]?key|access[_-]?token|authorization|password|passwd|bearer)\\s*[:=]\\s*[^\\s,;]+|sk-[a-z0-9_-]{16,}");
    private static final Pattern STABLE_PLAYER_ID = Pattern.compile(
            "(?i)(?:player(?:Id|Uuid|NetworkId)|accountId)\\s*[:=]\\s*[a-z0-9_-]{6,}|[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}");
    private static final Pattern HIDDEN_REASONING = Pattern.compile(
            "(?i)(?:chain[_ -]?of[_ -]?thought|hidden[_ -]?reasoning|scratchpad|private[_ -]?rationale)\\s*[:=]");

    public CurationPrivacyPolicy {
        if (schemaVersion != SCHEMA_VERSION || policyId == null || policyId.isBlank()
                || reviewedAt == null) throw new IllegalArgumentException(
                        "versioned privacy policy required");
        decisionBasis = decisionBasis == null ? "" : decisionBasis.strip();
    }

    public static CurationPrivacyPolicy failClosedDefault() {
        return new CurationPrivacyPolicy(SCHEMA_VERSION, "orbis-curation-privacy-v1",
                false, false, false,
                "Synthetic/project fixtures only; real-player export requires explicit review.",
                Instant.parse("2026-09-03T00:00:00Z"));
    }

    public String policyHash() { return CanonicalJson.sha256(this); }

    public PrivacyDecision evaluate(CurationRequest request) {
        List<String> reasons = new ArrayList<>();
        if (request.sourceKind() == SourceKind.REAL_PLAYER_PRODUCTION
                && !realPlayerCorpusApproved) {
            reasons.add("NEEDS_REVIEW_REAL_PLAYER_CONSENT");
        }
        if (request.containsRawAudio() && !permitRawAudio) reasons.add("PRIVACY_RAW_AUDIO");
        String payload = CanonicalJson.serialize(request.candidate().productionInput())
                + "\n" + request.chosenResponse() + "\n" + request.publicCritique();
        if (HIDDEN_REASONING.matcher(payload).find()) reasons.add("HIDDEN_REASONING_PRESENT");
        if (SECRET.matcher(payload).find()) reasons.add("PRIVACY_SECRET_OR_CREDENTIAL");
        if (WINDOWS_USER_PATH.matcher(payload).find()) reasons.add("PRIVACY_PRIVATE_PATH");
        if (!permitStablePlayerIdentifiers && STABLE_PLAYER_ID.matcher(payload).find()) {
            reasons.add("PRIVACY_STABLE_PLAYER_ID");
        }
        boolean reviewOnly = reasons.size() == 1
                && reasons.getFirst().equals("NEEDS_REVIEW_REAL_PLAYER_CONSENT");
        return new PrivacyDecision(reasons.isEmpty(), reviewOnly, List.copyOf(reasons),
                CanonicalJson.sha256(payload.toLowerCase(Locale.ROOT)));
    }

    /** Pseudonymization is deterministic and must happen before a new production snapshot. */
    public String pseudonymizeDisplayName(String displayName, String stableScope) {
        String token = CanonicalJson.sha256((stableScope == null ? "" : stableScope)
                + "\u0000" + (displayName == null ? "" : displayName));
        return "Player-" + token.substring(0, 10);
    }

    public record PrivacyDecision(boolean clean, boolean reviewOnly,
            List<String> reasonCodes, String scannedPayloadSha256) {
        public PrivacyDecision {
            reasonCodes = List.copyOf(reasonCodes == null ? List.of() : reasonCodes);
        }
    }
}
