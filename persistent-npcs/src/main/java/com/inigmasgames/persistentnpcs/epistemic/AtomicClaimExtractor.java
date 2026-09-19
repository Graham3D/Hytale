package com.inigmasgames.persistentnpcs.epistemic;

import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Bounded typed extraction for the high-value E3 claim classes; not a universal ontology. */
public final class AtomicClaimExtractor {
    private static final Pattern SUBJECTIVE = Pattern.compile(
            "(?i)\\bI\\s+(?:like|love|dislike|hate|want|wish|hope|prefer|feel|think)\\b");
    private static final Pattern DESIRE = Pattern.compile(
            "(?i)\\bI\\s+(?:want|wish|hope|would like|'d like)\\s+(.+)");
    private static final Pattern EMOTION = Pattern.compile(
            "(?i)\\bI(?:'m| am)?\\s*(?:feel|feeling)\\s+(.+)");
    private static final Pattern HYPOTHETICAL = Pattern.compile(
            "(?i)^(?:(?:only\\s+)?if|suppose|imagine|hypothetically)\\b|"
                    + "\\b(?:would|could|might|may)\\b");
    private static final Pattern METAPHOR = Pattern.compile(
            "(?i)\\b(?:like a|like an|as if|metaphorically|figure of speech|"
                    + "storm in a teacup|heart of (?:stone|gold)|fire in (?:your|my) belly)\\b");
    private static final Pattern HOLDING = Pattern.compile(
            "(?i)\\b(?:you(?:'re| are)\\s+(?:holding|carrying)|you(?:'ve| have) got|"
                    + "in your hand is)\\s+(?:an? |the )?([\\p{L}][\\p{L} '-]{0,60})");
    private static final Pattern NAME = Pattern.compile(
            "(?i)\\b(?:your name is|you told me your name is|you(?:'re| are) called)\\s+"
                    + "([\\p{L}][\\p{L}'-]{0,31})\\b");
    private static final Pattern PROPERTY = Pattern.compile(
            "(?i)\\b(?:your |the |this |that )?([\\p{L}][\\p{L}'-]{1,40})\\s+"
                    + "(?:is|looks|seems)\\s+(flickering|dim|hot|cold|damaged|broken|"
                    + "sharp|dull|lit|unlit|empty|full)\\b");
    private static final Pattern RELATIONSHIP = Pattern.compile(
            "(?i)\\b(?:I\\s+(?:have|had|know)|my)\\s+(?:(?:a|an|some|any|the)\\s+)?"
                    + "(friends?|family|parents?|siblings?|brothers?|sisters?|children?|"
                    + "crew|companions?|partners?|spouse|husband|wife)\\b");
    private static final Pattern POSSESSION = Pattern.compile(
            "(?i)\\bI\\s+(?:have|own|carry|keep|possess)\\s+(?:(?:a|an|some|the)\\s+)?"
                    + "([\\p{L}][\\p{L}' -]{1,60})");
    private static final Pattern SELF_PAST_EVENT = Pattern.compile(
            "(?i)\\b(?:I|we)\\s+(?:once\\s+|formerly\\s+|previously\\s+|last\\s+"
                    + "(?:night|week|year)\\s+|(?:have|had)\\s+)?(?:met|visited|went|lived|"
                    + "worked|found|lost|fought|escaped|learned|grew up|used to|saw|"
                    + "witnessed|hide|hid|hidden|left|put|dropped)\\b");
    private static final Pattern PLAYER_PAST_EVENT = Pattern.compile(
            "(?i)\\byou\\s+(?:(?:have|had)\\s+)?(?:met|visited|went|lived|worked|found|"
                    + "lost|fought|escaped|learned|grew up|used to|saw|witnessed|hide|hid|"
                    + "hidden|left|put|dropped)\\b");
    private static final Pattern LOCATION = Pattern.compile(
            "(?i)\\bI(?:'m| am)\\s+(?:at|in|inside|outside|near)\\s+(.{2,80})");
    private static final Pattern CURRENT_TASK = Pattern.compile(
            "(?i)\\bI(?:'m| am)\\s+(?:going|heading|walking|working|waiting|guarding|"
                    + "following|traveling|travelling)\\b(.*)");
    private static final Pattern ACTION_COMMITMENT = Pattern.compile(
            "(?i)\\bI(?:'ll| will| am going to)\\s+(follow|go|give|sell|bring|take|"
                    + "craft|repair|deliver|wait)\\b(.*)");
    private static final Pattern COMPLETED_ACTION = Pattern.compile(
            "(?i)\\bI(?:'ve| have)?\\s*(?:already\\s+)?(?:gave|sold|delivered|finished|"
                    + "completed|crafted|repaired|followed|placed)\\b(.*)");
    private static final Pattern CAPABILITY = Pattern.compile(
            "(?i)\\bI can\\s+(follow|go|give|sell|bring|take|craft|repair|deliver|wait)\\b");
    private static final Pattern QUANTITY = Pattern.compile(
            "(?i)\\b(?:there (?:are|is)|you (?:have|hold|carry)|I (?:have|own|carry))\\s+"
                    + "(\\d{1,9})\\s+([\\p{L}][\\p{L}'-]{0,40})");
    private static final Pattern FAMILY_PASSIVE = Pattern.compile(
            "(?i)\\bmy\\s+(brother|sister|mother|father|parent|child|friend|companion)\\b");
    private static final Pattern OBJECTIVE_COPULA = Pattern.compile(
            "(?i)\\b(?:is|are|was|were|has|have|happened|exists?|located|contains?)\\b");
    private static final Pattern UNCERTAINTY = Pattern.compile(
            "(?i)\\b(?:I (?:do not|don['’]t) know|I (?:do not|don['’]t) remember|"
                    + "I can['’]t tell|I cannot tell|I haven['’]t seen|"
                    + "I have not seen|unclear|conflicting|not sure)\\b");
    private static final Pattern CLARIFYING_QUESTION = Pattern.compile(
            "(?i)^(?:could|can|would|will) you (?:please )?(?:clarify|specify|identify|"
                    + "tell me which|tell me where)\\b|^(?:what|which|who|where|when|how)\\b");
    private static final Pattern INVENTED_NAMED_ACTOR = Pattern.compile(
            "\\b[A-Z][a-z]{2,24}\\b");

    public List<AtomicClaim> extract(String dialogue) {
        String text = dialogue == null ? "" : dialogue.strip();
        if (text.isBlank()) return List.of();
        ArrayList<AtomicClaim> claims = new ArrayList<>();
        BreakIterator sentences = BreakIterator.getSentenceInstance(Locale.ROOT);
        sentences.setText(text);
        int start = sentences.first();
        int sequence = 0;
        for (int end = sentences.next(); end != BreakIterator.DONE;
                start = end, end = sentences.next()) {
            String sentence = text.substring(start, end);
            int local = 0;
            Matcher splitter = Pattern.compile("(?i),\\s+(?:and|but)\\s+|;\\s+|\\s+because\\s+")
                    .matcher(sentence);
            while (splitter.find()) {
                sequence = extractClause(text, start + local, start + splitter.start(),
                        sequence, claims);
                local = splitter.end();
            }
            sequence = extractClause(text, start + local, end, sequence, claims);
        }
        return List.copyOf(claims);
    }

    private static int extractClause(String whole, int rawStart, int rawEnd, int sequence,
            List<AtomicClaim> out) {
        int start = rawStart, end = rawEnd;
        while (start < end && Character.isWhitespace(whole.charAt(start))) start++;
        while (end > start && (Character.isWhitespace(whole.charAt(end - 1))
                || ".,;!?".indexOf(whole.charAt(end - 1)) >= 0)) end--;
        if (end <= start) return sequence;
        String clause = whole.substring(start, end).strip();
        ClaimSpec spec = classify(clause);
        out.add(new AtomicClaim(AtomicClaim.SCHEMA_VERSION, "claim-" + sequence,
                spec.subject, spec.predicate, spec.object, spec.mode, spec.temporal,
                start, end, clause, List.of()));
        return sequence + 1;
    }

    private static ClaimSpec classify(String clause) {
        Matcher value;
        if ((value = HOLDING.matcher(clause)).find()) return objective("CURRENT_PLAYER",
                "HELD_ITEM", cleanObject(value.group(1)), "CURRENT");
        if ((value = DESIRE.matcher(clause)).find()) return new ClaimSpec("CURRENT_NPC",
                "DESIRE", cleanObject(value.group(1)), ClaimMode.DESIRE, "SUBJECTIVE");
        if ((value = EMOTION.matcher(clause)).find()) return new ClaimSpec("CURRENT_NPC",
                "EMOTION", cleanObject(value.group(1)), ClaimMode.EMOTION, "CURRENT");
        if ((value = NAME.matcher(clause)).find()) return objective("CURRENT_PLAYER",
                "NAME", value.group(1), "PERSISTENT");
        if ((value = PROPERTY.matcher(clause)).find()) return objective(
                "OBJECT:" + key(value.group(1)), "PROPERTY:" + key(value.group(2)),
                "TRUE", "CURRENT");
        if ((value = QUANTITY.matcher(clause)).find()) return objective("CURRENT_CONTEXT",
                "QUANTITY:" + key(value.group(2)), value.group(1), "CURRENT");
        if ((value = ACTION_COMMITMENT.matcher(clause)).find()) return new ClaimSpec(
                "CURRENT_NPC", "ACTION_COMMITMENT", key(value.group(1)),
                ClaimMode.COMMITMENT, "FUTURE");
        if ((value = COMPLETED_ACTION.matcher(clause)).find()) return new ClaimSpec(
                "CURRENT_NPC", "ACTION_RESULT", key(clause), ClaimMode.OBJECTIVE_FACT, "PAST");
        if ((value = CAPABILITY.matcher(clause)).find()) return objective("CURRENT_NPC",
                "ACTION_CAPABILITY", key(value.group(1)) + "_PLAYER", "CURRENT");
        if ((value = CURRENT_TASK.matcher(clause)).find()) return objective("CURRENT_NPC",
                "CURRENT_TASK", key(clause), "CURRENT");
        if ((value = LOCATION.matcher(clause)).find()) return objective("CURRENT_NPC",
                "CURRENT_LOCATION", cleanObject(value.group(1)), "CURRENT");
        if ((value = RELATIONSHIP.matcher(clause)).find()) return objective("CURRENT_NPC",
                "RELATIONSHIP", key(value.group(1)), "PERSISTENT");
        if ((value = FAMILY_PASSIVE.matcher(clause)).find()) return objective("CURRENT_NPC",
                "RELATIONSHIP", key(value.group(1)), "PERSISTENT");
        if (PLAYER_PAST_EVENT.matcher(clause).find()) return objective("CURRENT_PLAYER",
                "PAST_EVENT", key(clause), "HISTORICAL");
        if (SELF_PAST_EVENT.matcher(clause).find()) return objective("CURRENT_NPC", "PAST_EVENT",
                key(clause), "HISTORICAL");
        if ((value = POSSESSION.matcher(clause)).find()) return objective("CURRENT_NPC",
                "POSSESSION", cleanObject(value.group(1)), "CURRENT");
        if (UNCERTAINTY.matcher(clause).find()) return new ClaimSpec("CURRENT_NPC",
                "EPISTEMIC_UNCERTAINTY", key(clause), ClaimMode.SUBJECTIVE_OPINION,
                "CURRENT_TURN");
        if (clause.endsWith("?") || CLARIFYING_QUESTION.matcher(clause).find()) {
            return new ClaimSpec("", "CLARIFYING_QUESTION", key(clause), ClaimMode.QUESTION,
                    "CURRENT_TURN");
        }
        if (METAPHOR.matcher(clause).find()) return new ClaimSpec("", "METAPHOR",
                key(clause), ClaimMode.METAPHOR, "SUBJECTIVE");
        if (SUBJECTIVE.matcher(clause).find()) return new ClaimSpec("CURRENT_NPC", "SUBJECTIVE",
                key(clause), ClaimMode.SUBJECTIVE_OPINION, "SUBJECTIVE");
        if (HYPOTHETICAL.matcher(clause).find()) return new ClaimSpec("", "HYPOTHETICAL",
                key(clause), ClaimMode.HYPOTHETICAL, "HYPOTHETICAL");
        String lower = clause.toLowerCase(Locale.ROOT);
        if (lower.matches("^(?:hello|hi|hey|thanks|thank you|hm+|heh|ha|yes|no|perhaps|"
                + "of course|all right|alright|good|fine)(?:[ ,].*)?$")) {
            return new ClaimSpec("", "SOCIAL_EXPRESSION", key(clause),
                    ClaimMode.SUBJECTIVE_OPINION, "SUBJECTIVE");
        }
        Matcher namedActor = INVENTED_NAMED_ACTOR.matcher(clause);
        boolean hasEmbeddedNamedActor = namedActor.find() && namedActor.start() > 0;
        if (OBJECTIVE_COPULA.matcher(clause).find() || hasEmbeddedNamedActor) {
            return new ClaimSpec("UNRESOLVED", "UNPARSEABLE_OBJECTIVE", key(clause),
                    ClaimMode.OBJECTIVE_FACT, "UNRESOLVED");
        }
        // Direct short values (for example "Graham.") remain unresolved until the AnswerPlan
        // binds them to a required slot in the firewall.
        return new ClaimSpec("", "ANSWER_VALUE", cleanObject(clause),
                ClaimMode.OBJECTIVE_FACT, "CURRENT_TURN");
    }

    private static ClaimSpec objective(String subject, String predicate, String object,
            String temporal) { return new ClaimSpec(subject, predicate, object,
            ClaimMode.OBJECTIVE_FACT, temporal); }
    private static String cleanObject(String value) { return value == null ? "" : value
            .replaceAll("(?i)\\b(?:right now|currently)\\b", "")
            .replaceAll("\\s+", " ").strip(); }
    private static String key(String value) { return cleanObject(value).toUpperCase(Locale.ROOT)
            .replaceAll("[^\\p{L}\\p{N}]+", "_").replaceAll("^_+|_+$", ""); }
    private record ClaimSpec(String subject, String predicate, String object, ClaimMode mode,
            String temporal) { }
}
