package com.inigmasgames.persistentnpcs.sentinel;

import com.inigmasgames.persistentnpcs.sentinel.SentinelContracts.InvariantDefinition;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

/** Stable fingerprint: excludes IDs, text, timestamps and exact numeric pressure values. */
public final class FailureSignatureEngine {
    public String signature(InvariantDefinition definition, String reasonCode,
            SentinelObservation observation) {
        String provider = stable(observation.fact("provider"));
        String route = stable(observation.fact("route"));
        String output = stable(observation.fact("outputContract"));
        String policy = stable(observation.fact("policyVersion"));
        String config = stable(observation.fact("configurationHash"));
        String normalized = definition.id() + '|' + definition.category() + '|'
                + definition.boundary() + '|' + stable(reasonCode) + '|' + provider + '|'
                + route + '|' + output + '|' + policy + '|' + config;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(normalized.getBytes(StandardCharsets.UTF_8));
            return definition.id() + ':' + HexFormat.of().formatHex(digest, 0, 10);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
    private static String stable(String value) {
        return value == null ? "" : value.strip().toUpperCase(Locale.ROOT)
                .replaceAll("\\b\\d+(?:\\.\\d+)?\\s*(?:MIB|MS|TOKENS?)?\\b", "#")
                .replaceAll("\\s+", " ");
    }
}
