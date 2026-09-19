package com.inigmasgames.persistentnpcs.conversation.contract;

/** Versioned provider-output protocol selected independently of cognition depth. */
public record DecisionContract(
        Kind kind,
        String schemaVersion,
        boolean structured,
        int maximumOutputTokens,
        int boundedWorstCaseSerializedTokens) {

    public enum Kind {
        DIALOGUE_TEXT,
        ACTION_RESULT_DIALOGUE,
        DELIBERATIVE_MEMO,
        COMPACT_CHOICE,
        COMPACT_DELIBERATIVE_FINAL,
        AUTONOMOUS_DECISION
    }

    public DecisionContract {
        if (kind == null) throw new IllegalArgumentException("decision contract kind required");
        schemaVersion = schemaVersion == null || schemaVersion.isBlank()
                ? "v1" : schemaVersion.strip();
        if (maximumOutputTokens < 16 || boundedWorstCaseSerializedTokens < 1) {
            throw new IllegalArgumentException("bounded decision-contract budgets required");
        }
    }

    public static DecisionContract dialogue(boolean grounded) {
        return new DecisionContract(Kind.DIALOGUE_TEXT, "dialogue-text-v1", false,
                grounded ? 112 : 80, grounded ? 88 : 64);
    }

    public static DecisionContract actionResult() {
        return new DecisionContract(Kind.ACTION_RESULT_DIALOGUE,
                "action-result-dialogue-v1", false, 112, 88);
    }

    public static DecisionContract choice() {
        return new DecisionContract(Kind.COMPACT_CHOICE, "compact-choice-v1", true,
                256, 200);
    }

    public static DecisionContract deliberativeFinal() {
        return new DecisionContract(Kind.COMPACT_DELIBERATIVE_FINAL,
                "compact-deliberative-final-v1", true, 256, 192);
    }

    public static DecisionContract deliberativeMemo() {
        return new DecisionContract(Kind.DELIBERATIVE_MEMO,
                "deliberative-memo-v1", false, 112, 88);
    }

    public static DecisionContract autonomous() {
        return new DecisionContract(Kind.AUTONOMOUS_DECISION,
                "autonomous-decision-v1", true, 320, 240);
    }
}
