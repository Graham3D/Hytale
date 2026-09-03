package com.inigmasgames.persistentnpcs.orbis;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Final source-ownership and runtime-policy regression gate. */
public final class R048OrbisFinalHardeningTest {
    private R048OrbisFinalHardeningTest() { }

    public static void main(String[] args) throws Exception {
        legacyOwnersArePhysicallyAbsent();
        nativeAdminPolicyIsLiveAndPersistent();
        shutdownAndTraceCallbacksAreNonBlocking();
        System.out.println("R048 Orbis final hardening tests passed.");
    }

    private static void legacyOwnersArePhysicallyAbsent() throws Exception {
        Path root = Path.of("src/main/java/com/inigmasgames/persistentnpcs");
        assert !Files.exists(root.resolve("voice/HytaleVoicePipeline.java"));
        assert !Files.exists(root.resolve("cognition/ResponseAuthorityRegistry.java"));
        assert !Files.exists(root.resolve("llm/LlmRequestBudget.java"));
        assert !Files.exists(root.resolve("orbis/OrbisProviderAttribution.java"));
        String bridge = Files.readString(root.resolve("hytale/HytaleConversationBridge.java"));
        String audience = Files.readString(root.resolve("orbis/OrbisAudienceGateway.java"));
        String invocation = Files.readString(root.resolve(
                "conversation/ConversationInvocation.java"));
        String config = Files.readString(root.resolve("config/FrameworkConfig.java"));
        String defaults = Files.readString(Path.of("src/main/resources/defaults/config.json"));
        assert bridge.contains("orbis.submitText(");
        assert bridge.contains("converseForOrbis(");
        assert !bridge.contains("converseWithVoice(");
        assert !audience.contains("void dispatch(");
        assert !invocation.contains("legacyResponseAuthority");
        assert !config.contains("queueTimeoutMillis");
        assert !defaults.contains("queueTimeoutMillis");
    }

    private static void nativeAdminPolicyIsLiveAndPersistent() throws Exception {
        AtomicReference<ResourcePolicy> persisted = new AtomicReference<>();
        try (OrbisResourceScheduler scheduler = new OrbisResourceScheduler(
                OrbisResourceConfig.defaults(), () -> null, ignored -> { }, persisted::set)) {
            ResourcePolicy selected = scheduler.selectPolicy(ResourcePolicy.CPU_FIRST)
                    .get(1, TimeUnit.SECONDS);
            assert selected == ResourcePolicy.CPU_FIRST;
            assert persisted.get() == ResourcePolicy.CPU_FIRST;
            assert scheduler.snapshot().policy() == ResourcePolicy.CPU_FIRST;
        }
        String page = Files.readString(Path.of(
                "src/main/java/com/inigmasgames/persistentnpcs/ui/CognitionInspectorPage.java"));
        String ui = Files.readString(Path.of(
                "src/main/resources/Common/UI/Custom/Pages/ImmersiveNpcCognitionInspector.ui"));
        for (String policy : new String[] {"GPUHEAVY", "BALANCED", "CPUFIRST",
                "CPUONLY", "REMOTEAI"}) assert ui.contains("#Policy" + policy + "Button");
        assert page.contains("SelectResourcePolicy");
        assert page.contains("selectResourcePolicy(requested)");
    }

    private static void shutdownAndTraceCallbacksAreNonBlocking() throws Exception {
        String coordinator = Files.readString(Path.of(
                "src/main/java/com/inigmasgames/persistentnpcs/orbis/OrbisTurnCoordinator.java"));
        String traces = Files.readString(Path.of(
                "src/main/java/com/inigmasgames/persistentnpcs/diagnostics/NpcTraceManager.java"));
        String install = Files.readString(Path.of("install.ps1"));
        assert !coordinator.contains(".join();");
        assert traces.contains("toggleAsync(");
        assert install.contains("logs\\npcs");
        assert install.contains("Remove-Item -LiteralPath $obsoleteNpcLogs -Recurse -Force");
    }
}
