package com.inigmasgames.persistentnpcs.profile;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Server-owned, revision-bound profile draft. Custom UI values are intent only. */
public final class NpcProfileDraft {
    public enum Field {
        ROLE("role", 160, false, true),
        SELF_IDENTITY("selfIdentity", 400, false, false),
        SPECIES_ARCHETYPE("speciesArchetype", 80, false, false),
        AGE_CATEGORY("ageCategory", 40, false, false),
        HOME("home", 200, false, false),
        SUMMARY("summary", 500, false, false),
        WORKPLACE("workplace", 200, false, false),
        PERSONALITY("personality", 900, false, true),
        PERSONALITY_TRAITS("personalityTraits", 700, true, true),
        VALUES("values", 700, true, true),
        LIKES("likes", 700, true, false),
        DISLIKES("dislikes", 700, true, false),
        FEARS("fears", 700, true, false),
        BIOGRAPHY("biography", 1800, false, true),
        PURPOSE("purpose", 900, false, true),
        GOALS("goals", 900, true, true),
        SPEAKING_STYLE("speakingStyle", 900, false, true),
        KNOWLEDGE_DOMAINS("knowledgeDomains", 900, true, false),
        CREATOR_NOTES("creatorNotes", 3000, false, false);

        private final String jsonName;
        private final int maxLength;
        private final boolean list;
        private final boolean generated;

        Field(String jsonName, int maxLength, boolean list, boolean generated) {
            this.jsonName = jsonName;
            this.maxLength = maxLength;
            this.list = list;
            this.generated = generated;
        }

        public String jsonName() { return jsonName; }
        public int maxLength() { return maxLength; }
        public boolean list() { return list; }
        public boolean generated() { return generated; }
    }

    public enum Provenance { HUMAN_DRAFT, GENERATED_PROPOSAL, GENERATED_ACCEPTED_DRAFT }

    public record Proposal(UUID requestId, String scope, String provider, String model,
            Instant createdAt, Map<Field, String> changes, List<String> warnings) {
        public Proposal {
            changes = Map.copyOf(changes == null ? Map.of() : changes);
            warnings = List.copyOf(warnings == null ? List.of() : warnings);
        }
    }

    private final UUID draftId;
    private final UUID sessionId;
    private final UUID stableNpcId;
    private final long editorGeneration;
    private final long baseRevision;
    private final String baseHash;
    private final String profileName;
    private final JsonObject baseDocument;
    private final EnumMap<Field, String> initial = new EnumMap<>(Field.class);
    private final EnumMap<Field, String> values = new EnumMap<>(Field.class);
    private final EnumSet<Field> dirty = EnumSet.noneOf(Field.class);
    private Proposal proposal;
    private Proposal acceptedProposal;
    private Provenance provenance = Provenance.HUMAN_DRAFT;

    NpcProfileDraft(UUID sessionId, UUID stableNpcId, long editorGeneration,
            long baseRevision, String baseHash, String profileName, JsonObject document) {
        this.draftId = UUID.randomUUID();
        this.sessionId = sessionId;
        this.stableNpcId = stableNpcId;
        this.editorGeneration = editorGeneration;
        this.baseRevision = baseRevision;
        this.baseHash = baseHash;
        this.profileName = profileName;
        this.baseDocument = document.deepCopy();
        for (Field field : Field.values()) {
            String value = displayValue(document.get(field.jsonName()), field.list());
            // R164 could persist an unresolved Custom UI selector as literal text.
            // Expose that invalid legacy value as an empty repairable draft field;
            // canon remains unchanged unless the creator explicitly saves.
            if (isUiSelectorLiteral(value)) value = "";
            initial.put(field, value);
            values.put(field, value);
        }
    }

    public UUID draftId() { return draftId; }
    public UUID sessionId() { return sessionId; }
    public UUID stableNpcId() { return stableNpcId; }
    public long editorGeneration() { return editorGeneration; }
    public long baseRevision() { return baseRevision; }
    public String baseHash() { return baseHash; }
    public String profileName() { return profileName; }
    public String value(Field field) { return values.getOrDefault(field, ""); }
    public Set<Field> dirtyFields() { return Set.copyOf(dirty); }
    public boolean dirty() { return !dirty.isEmpty(); }
    public Proposal proposal() { return proposal; }
    public Proposal acceptedProposal() { return acceptedProposal; }
    public Provenance provenance() { return provenance; }

    public void update(Field field, String value) {
        if (field == null) throw new IllegalArgumentException("Profile field is required.");
        String clean = value == null ? "" : value.strip();
        if (isUiSelectorLiteral(clean)) {
            throw new IllegalArgumentException(
                    "Profile values cannot contain unresolved UI selectors.");
        }
        if (clean.length() > field.maxLength()) {
            throw new IllegalArgumentException(field.name() + " exceeds "
                    + field.maxLength() + " characters.");
        }
        values.put(field, clean);
        if (clean.equals(initial.get(field))) dirty.remove(field); else dirty.add(field);
        provenance = Provenance.HUMAN_DRAFT;
    }

    public void reset() {
        values.clear();
        values.putAll(initial);
        dirty.clear();
        proposal = null;
        acceptedProposal = null;
        provenance = Provenance.HUMAN_DRAFT;
    }

    public void setProposal(Proposal value) {
        proposal = value;
        provenance = Provenance.GENERATED_PROPOSAL;
    }

    public void discardProposal() {
        proposal = null;
        provenance = Provenance.HUMAN_DRAFT;
    }

    public void acceptProposal(Set<Field> selected) {
        if (proposal == null) throw new IllegalStateException("No generated proposal is active.");
        Set<Field> accepted = selected == null || selected.isEmpty()
                ? proposal.changes().keySet() : selected;
        EnumMap<Field, String> acceptedChanges = new EnumMap<>(Field.class);
        for (var entry : proposal.changes().entrySet()) {
            if (accepted.contains(entry.getKey())) {
                update(entry.getKey(), entry.getValue());
                acceptedChanges.put(entry.getKey(), entry.getValue());
            }
        }
        if (acceptedChanges.isEmpty()) throw new IllegalArgumentException(
                "None of the selected fields are present in the proposal.");
        provenance = Provenance.GENERATED_ACCEPTED_DRAFT;
        acceptedProposal = new Proposal(proposal.requestId(), proposal.scope(),
                proposal.provider(), proposal.model(), proposal.createdAt(),
                acceptedChanges, proposal.warnings());
        proposal = null;
    }

    public JsonObject candidateDocument() {
        JsonObject candidate = baseDocument.deepCopy();
        for (Field field : Field.values()) {
            String value = values.getOrDefault(field, "");
            if (field.list()) {
                JsonArray array = new JsonArray();
                for (String item : parseList(value)) array.add(item);
                candidate.add(field.jsonName(), array);
            } else {
                candidate.add(field.jsonName(), new JsonPrimitive(value));
            }
        }
        return candidate;
    }

    public String draftHash() {
        return sha256(candidateDocument().toString().getBytes(StandardCharsets.UTF_8));
    }

    private static String displayValue(JsonElement element, boolean list) {
        if (element == null || element.isJsonNull()) return "";
        if (!list) return element.isJsonPrimitive() ? element.getAsString() : element.toString();
        if (!element.isJsonArray()) return "";
        List<String> values = new ArrayList<>();
        for (JsonElement item : element.getAsJsonArray()) {
            if (item != null && item.isJsonPrimitive()) values.add(item.getAsString());
        }
        return String.join("\n", values);
    }

    static boolean isUiSelectorLiteral(String value) {
        if (value == null) return false;
        String clean = value.strip();
        return clean.startsWith("#Profile") && clean.endsWith("Input.Value");
    }

    static List<String> parseList(String text) {
        if (text == null || text.isBlank()) return List.of();
        return text.lines().flatMap(line -> java.util.Arrays.stream(line.split(",")))
                .map(String::strip).filter(value -> !value.isBlank()).distinct().toList();
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().withUpperCase().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
