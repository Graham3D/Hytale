package com.inigmasgames.persistentnpcs.orbis;

import com.inigmasgames.persistentnpcs.ai.AiResourceRequirements;

/** Measured contract for sustaining the whole foreground conversation pipeline. */
public record ConversationOperatingEnvelope(
        String model,
        String hardwareProfile,
        long hytaleSafetyReserveMiB,
        long nemotronIncrementalMiB,
        long nemotronWorkspaceMiB,
        long chatterboxIncrementalMiB,
        long chatterboxWorkspaceMiB,
        long preferredDriftAllowanceMiB,
        long degradedDriftAllowanceMiB) {
    public ConversationOperatingEnvelope {
        model = model == null ? "" : model;
        hardwareProfile = hardwareProfile == null ? "UNKNOWN" : hardwareProfile;
        hytaleSafetyReserveMiB = Math.max(512, hytaleSafetyReserveMiB);
        nemotronIncrementalMiB = Math.max(0, nemotronIncrementalMiB);
        nemotronWorkspaceMiB = Math.max(0, nemotronWorkspaceMiB);
        chatterboxIncrementalMiB = Math.max(0, chatterboxIncrementalMiB);
        chatterboxWorkspaceMiB = Math.max(0, chatterboxWorkspaceMiB);
        preferredDriftAllowanceMiB = Math.max(0, preferredDriftAllowanceMiB);
        degradedDriftAllowanceMiB = Math.max(0, Math.min(
                preferredDriftAllowanceMiB, degradedDriftAllowanceMiB));
    }

    public static ConversationOperatingEnvelope measured(String model, String profile,
            long reserve, AiResourceRequirements llm, AiResourceRequirements tts) {
        return new ConversationOperatingEnvelope(model, profile, reserve,
                gpuIncremental(llm), gpuWorkspace(llm), gpuIncremental(tts),
                gpuWorkspace(tts), 96, 32);
    }

    public long immediateRequiredMiB() {
        return hytaleSafetyReserveMiB + Math.max(
                nemotronIncrementalMiB + nemotronWorkspaceMiB,
                chatterboxIncrementalMiB + chatterboxWorkspaceMiB);
    }

    public long preferredRequiredMiB() {
        return immediateRequiredMiB() + preferredDriftAllowanceMiB;
    }

    public long degradedRequiredMiB() {
        return immediateRequiredMiB() + degradedDriftAllowanceMiB;
    }

    private static long gpuIncremental(AiResourceRequirements value) {
        return value != null && value.placement().usesLocalGpu()
                ? value.incrementalVramMiB() : 0;
    }

    private static long gpuWorkspace(AiResourceRequirements value) {
        return value != null && value.placement().usesLocalGpu()
                ? value.temporaryVramMiB() : 0;
    }
}
