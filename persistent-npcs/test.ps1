param(
    [string]$ServerJar = "$env:APPDATA\Hytale\install\pre-release\package\game\latest\Server\HytaleServer.jar",
    [string]$ReleaseServerJar = "$env:APPDATA\Hytale\install\release\package\game\latest\Server\HytaleServer.jar",
    [switch]$SkipLive
)

$ErrorActionPreference = 'Stop'
$projectRoot = $PSScriptRoot
$bundledJdk = Join-Path $projectRoot '..\Hytale Taverns\.tools\jdk-25.0.4+7\bin'
$javac = Join-Path $bundledJdk 'javac.exe'
$java = Join-Path $bundledJdk 'java.exe'
if (-not (Test-Path -LiteralPath $javac)) {
    $javac = (Get-Command javac -ErrorAction Stop).Source
    $java = (Get-Command java -ErrorAction Stop).Source
}

& (Join-Path $projectRoot 'build.ps1') -ServerJar $ServerJar
if ($LASTEXITCODE -ne 0) {
    throw "build.ps1 failed with exit code $LASTEXITCODE"
}

$testClasses = Join-Path $projectRoot 'build\test-classes'
$resolvedTests = [IO.Path]::GetFullPath($testClasses)
$resolvedBuildRoot = [IO.Path]::GetFullPath((Join-Path $projectRoot 'build'))
if (-not $resolvedTests.StartsWith($resolvedBuildRoot, [StringComparison]::OrdinalIgnoreCase)) {
    throw "Unsafe test output path: $resolvedTests"
}
if (Test-Path -LiteralPath $testClasses) {
    Remove-Item -LiteralPath $testClasses -Recurse -Force
}
New-Item -ItemType Directory -Force -Path $testClasses | Out-Null

$testSources = @(Get-ChildItem -LiteralPath (Join-Path $projectRoot 'src\test\java') -Recurse -File -Filter '*.java' | ForEach-Object FullName)
$classpath = (Join-Path $projectRoot 'build\classes') + ";" + $ServerJar
$testArgs = Join-Path $resolvedBuildRoot 'javac-test.args'
$arguments = @(
    '--add-modules', 'jdk.httpserver', '-encoding', 'UTF-8', '-source', '25',
    '-target', '25', '-classpath', ('"' + $classpath.Replace('\', '/') + '"'),
    '-d', ('"' + $testClasses.Replace('\', '/') + '"')
) + @($testSources | ForEach-Object { '"' + $_.Replace('\', '/') + '"' })
[IO.File]::WriteAllLines($testArgs, $arguments, [Text.UTF8Encoding]::new($false))
& $javac "@$testArgs"
if ($LASTEXITCODE -ne 0) {
    throw "Test compilation failed with exit code $LASTEXITCODE"
}

$runtimeClasspath = $testClasses + ";" + $classpath
& $java --add-modules jdk.httpserver -ea -classpath $runtimeClasspath com.inigmasgames.persistentnpcs.MilestoneOneTest
if ($LASTEXITCODE -ne 0) {
    throw "Tests failed with exit code $LASTEXITCODE"
}

& $java --add-modules jdk.httpserver -ea -classpath $runtimeClasspath com.inigmasgames.persistentnpcs.IntelligenceFoundationTest
if ($LASTEXITCODE -ne 0) {
    throw "Intelligence foundation tests failed with exit code $LASTEXITCODE"
}

& $java --add-modules jdk.httpserver -ea -classpath $runtimeClasspath com.inigmasgames.persistentnpcs.FrameworkMilestoneTest
if ($LASTEXITCODE -ne 0) {
    throw "R006 framework tests failed with exit code $LASTEXITCODE"
}

& $java --add-modules jdk.httpserver -ea -classpath $runtimeClasspath com.inigmasgames.persistentnpcs.R008StabilizationTest
if ($LASTEXITCODE -ne 0) {
    throw "R008 stabilization tests failed with exit code $LASTEXITCODE"
}

& $java --add-modules jdk.httpserver -ea -classpath $runtimeClasspath com.inigmasgames.persistentnpcs.R010GroundingRegressionTest
if ($LASTEXITCODE -ne 0) {
    throw "R010 grounding regression tests failed with exit code $LASTEXITCODE"
}

& $java --add-modules jdk.httpserver -ea -classpath $runtimeClasspath com.inigmasgames.persistentnpcs.R011EnvironmentGroundingTest
if ($LASTEXITCODE -ne 0) {
    throw "R011 environment grounding tests failed with exit code $LASTEXITCODE"
}

& $java --add-modules jdk.httpserver -ea -classpath $runtimeClasspath com.inigmasgames.persistentnpcs.R012CognitionReliabilityTest
if ($LASTEXITCODE -ne 0) {
    throw "R012 cognition/reliability tests failed with exit code $LASTEXITCODE"
}

& $java --add-modules jdk.httpserver -ea -classpath $runtimeClasspath com.inigmasgames.persistentnpcs.R013MaraVoiceTest
if ($LASTEXITCODE -ne 0) {
    throw "R013 Mara voice tests failed with exit code $LASTEXITCODE"
}

& $java --add-modules jdk.httpserver -ea -classpath $runtimeClasspath com.inigmasgames.persistentnpcs.R014VoiceIntegrationTest
if ($LASTEXITCODE -ne 0) {
    throw "R014 Update 6 voice integration tests failed with exit code $LASTEXITCODE"
}

& $java -ea -classpath $runtimeClasspath com.inigmasgames.persistentnpcs.hytale.R015SpawnCompatibilityTest
if ($LASTEXITCODE -ne 0) {
    throw "R015 pre-release spawn compatibility test failed with exit code $LASTEXITCODE"
}
& $java -ea -classpath $runtimeClasspath com.inigmasgames.persistentnpcs.hytale.R016RuntimeCompatibilityTest update6
if ($LASTEXITCODE -ne 0) {
    throw "R016 pre-release runtime gate test failed with exit code $LASTEXITCODE"
}
& $java -ea -classpath $runtimeClasspath com.inigmasgames.persistentnpcs.R017HomeAppearanceTest
if ($LASTEXITCODE -ne 0) {
    throw "R017 home/follow/appearance tests failed with exit code $LASTEXITCODE"
}
& $java -ea -classpath $runtimeClasspath com.inigmasgames.persistentnpcs.R018SingleplayerVoiceSkinTest
if ($LASTEXITCODE -ne 0) {
    throw "R018 single-player voice/skin tests failed with exit code $LASTEXITCODE"
}
if (Test-Path -LiteralPath $ReleaseServerJar) {
    $releaseRuntimeClasspath = $testClasses + ";" + (Join-Path $projectRoot 'build\classes') + ";" + $ReleaseServerJar
    & $java -ea -classpath $releaseRuntimeClasspath com.inigmasgames.persistentnpcs.hytale.R015SpawnCompatibilityTest
    if ($LASTEXITCODE -ne 0) {
        throw "R015 release spawn compatibility test failed with exit code $LASTEXITCODE"
    }
    # The installed stable release channel now carries the same Update 6 NPC ABI.
    & $java -ea -classpath $releaseRuntimeClasspath com.inigmasgames.persistentnpcs.hytale.R016RuntimeCompatibilityTest update6
    if ($LASTEXITCODE -ne 0) {
        throw "R016 stable release Update 6 runtime test failed with exit code $LASTEXITCODE"
    }
}

& $java --add-modules jdk.httpserver -ea -classpath $runtimeClasspath com.inigmasgames.persistentnpcs.EmergentWorldMilestoneTest
if ($LASTEXITCODE -ne 0) {
    throw "Emergent-world milestone tests failed with exit code $LASTEXITCODE"
}

& $java --add-modules jdk.httpserver -ea -classpath $runtimeClasspath com.inigmasgames.persistentnpcs.R027ProfileDialogueRenameTest
if ($LASTEXITCODE -ne 0) {
    throw "R027 profile/dialogue/rename tests failed with exit code $LASTEXITCODE"
}

& $java --add-modules jdk.httpserver -ea -classpath $runtimeClasspath com.inigmasgames.persistentnpcs.R092NpcCreateInventoryUiTest
if ($LASTEXITCODE -ne 0) {
    throw "R092 NPC create native inventory/profile UI tests failed with exit code $LASTEXITCODE"
}

& $java --add-modules jdk.httpserver -ea -classpath $runtimeClasspath com.inigmasgames.persistentnpcs.R101NpcProfileTargetedRepairTest
if ($LASTEXITCODE -ne 0) {
    throw "R101 NPC profile targeted repair tests failed with exit code $LASTEXITCODE"
}

& $java --add-modules jdk.httpserver -ea -classpath $runtimeClasspath com.inigmasgames.persistentnpcs.R102NpcProfileNativeWindowTest
if ($LASTEXITCODE -ne 0) {
    throw "R102 NPC profile native-window tests failed with exit code $LASTEXITCODE"
}

& $java --add-modules jdk.httpserver -ea -classpath $runtimeClasspath com.inigmasgames.persistentnpcs.R103NpcProfileGridMaterializationTest
if ($LASTEXITCODE -ne 0) {
    throw "R103 NPC Profile grid materialization tests failed with exit code $LASTEXITCODE"
}

& $java --add-modules jdk.httpserver -ea -classpath $runtimeClasspath com.inigmasgames.persistentnpcs.R113NativeInventoryControlTest
if ($LASTEXITCODE -ne 0) {
    throw "R113 native inventory control structural tests failed with exit code $LASTEXITCODE"
}

& $java --add-modules jdk.httpserver -ea -classpath $runtimeClasspath com.inigmasgames.persistentnpcs.R114NativeNpcInventoryPersistenceProbeTest
if ($LASTEXITCODE -ne 0) {
    throw "R114 native live-NPC inventory persistence structural tests failed with exit code $LASTEXITCODE"
}

& $java --add-modules jdk.httpserver -ea -classpath $runtimeClasspath com.inigmasgames.persistentnpcs.R115NativeNpcInventoryProfileIntegrationTest
if ($LASTEXITCODE -ne 0) {
    throw "R115 native NPC inventory Profile integration tests failed with exit code $LASTEXITCODE"
}

& $java --add-modules jdk.httpserver -ea -classpath $runtimeClasspath com.inigmasgames.persistentnpcs.R117CustomGridPacketClosureTest
if ($LASTEXITCODE -ne 0) {
    throw "R116 Custom UI differential Probe 10 tests failed with exit code $LASTEXITCODE"
}

& $java --add-modules jdk.httpserver -ea -classpath $runtimeClasspath com.inigmasgames.persistentnpcs.R118ServerAuthoritativeCustomUiBridgeTest
if ($LASTEXITCODE -ne 0) {
    throw "R118 server-authoritative Custom UI bridge tests failed with exit code $LASTEXITCODE"
}

& $java --add-modules jdk.httpserver -ea -classpath $runtimeClasspath com.inigmasgames.persistentnpcs.R119NpcProfileProductionInventoryIntegrationTest
if ($LASTEXITCODE -ne 0) {
    throw "R119 NPC Profile production inventory integration tests failed with exit code $LASTEXITCODE"
}

& $java --add-modules jdk.httpserver -ea -classpath $runtimeClasspath com.inigmasgames.persistentnpcs.R120NpcProfileBoundGridHotfixTest
if ($LASTEXITCODE -ne 0) {
    throw "R120 NPC Profile bound-grid construction hotfix tests failed with exit code $LASTEXITCODE"
}

& $java --add-modules jdk.httpserver -ea -classpath $runtimeClasspath com.inigmasgames.persistentnpcs.R121NpcInventoryMetadataHotfixTest
if ($LASTEXITCODE -ne 0) {
    throw "R121 NPC inventory metadata hotfix tests failed with exit code $LASTEXITCODE"
}

& $java --add-modules jdk.httpserver -ea -classpath $runtimeClasspath com.inigmasgames.persistentnpcs.R122NpcInventoryPersistenceNormalizationTest
if ($LASTEXITCODE -ne 0) {
    throw "R122 NPC inventory persistence normalization tests failed with exit code $LASTEXITCODE"
}

& $java --add-modules jdk.httpserver -ea -classpath $runtimeClasspath com.inigmasgames.persistentnpcs.R123NpcProfileReopenWindowIdTest
if ($LASTEXITCODE -ne 0) {
    throw "R123 NPC Profile reopen window-ID tests failed with exit code $LASTEXITCODE"
}

& $java --add-modules jdk.httpserver -ea -classpath $runtimeClasspath com.inigmasgames.persistentnpcs.R124NpcProfilePolishTest
if ($LASTEXITCODE -ne 0) {
    throw "R124 NPC Profile polish tests failed with exit code $LASTEXITCODE"
}

& $java --add-modules jdk.httpserver -ea -classpath $runtimeClasspath com.inigmasgames.persistentnpcs.R129NpcAuthoringStudioA1Test
if ($LASTEXITCODE -ne 0) {
    throw "R129 NPC Authoring Studio A1 tests failed with exit code $LASTEXITCODE"
}

& $java --add-modules jdk.httpserver -ea -classpath $runtimeClasspath com.inigmasgames.persistentnpcs.R131NpcAuthoringStudioA2InventoryBridgeTest
if ($LASTEXITCODE -ne 0) {
    throw "R131 NPC Authoring Studio A2 inventory bridge tests failed with exit code $LASTEXITCODE"
}

& $java --add-modules jdk.httpserver -ea -classpath $runtimeClasspath com.inigmasgames.persistentnpcs.R132NpcAuthoringStudioA3GearStatsTest
if ($LASTEXITCODE -ne 0) {
    throw "R132 NPC Authoring Studio A3 gear/stats tests failed with exit code $LASTEXITCODE"
}

& $java --add-modules jdk.httpserver -ea -classpath $runtimeClasspath com.inigmasgames.persistentnpcs.R133NpcAuthoringStudioA4ProfileEditorTest
if ($LASTEXITCODE -ne 0) {
    throw "R133 NPC Authoring Studio A4 profile-editor tests failed with exit code $LASTEXITCODE"
}

& $java --add-modules jdk.httpserver -ea -classpath $runtimeClasspath com.inigmasgames.persistentnpcs.R134NpcAuthoringStudioA5AppearanceTest
if ($LASTEXITCODE -ne 0) {
    throw "R134 NPC Authoring Studio A5 appearance tests failed with exit code $LASTEXITCODE"
}

& $java --add-modules jdk.httpserver -ea -classpath $runtimeClasspath com.inigmasgames.persistentnpcs.R135NpcAuthoringStudioA6VoiceRecorderTest
if ($LASTEXITCODE -ne 0) {
    throw "R135 NPC Authoring Studio A6 Voice Recorder tests failed with exit code $LASTEXITCODE"
}

& $java --add-modules jdk.httpserver -ea -classpath $runtimeClasspath com.inigmasgames.persistentnpcs.R136NpcAuthoringStudioA6RepairTest
if ($LASTEXITCODE -ne 0) {
    throw "R136 NPC Authoring Studio A6 repair tests failed with exit code $LASTEXITCODE"
}

& $java --add-modules jdk.httpserver -ea -classpath $runtimeClasspath com.inigmasgames.persistentnpcs.R137NpcAuthoringStudioA6VoiceBindingTest
if ($LASTEXITCODE -ne 0) {
    throw "R137 NPC Authoring Studio A6 voice-binding tests failed with exit code $LASTEXITCODE"
}

& $java --add-modules jdk.httpserver -ea -classpath $runtimeClasspath com.inigmasgames.persistentnpcs.R138NpcAuthoringStudioA6WaveformBindingTest
if ($LASTEXITCODE -ne 0) {
    throw "R138 NPC Authoring Studio A6 waveform-binding tests failed with exit code $LASTEXITCODE"
}

& $java --add-modules jdk.httpserver -ea -classpath $runtimeClasspath com.inigmasgames.persistentnpcs.R139NpcAuthoringStudioP1VoicePolishTest
if ($LASTEXITCODE -ne 0) {
    throw "R139 NPC Authoring Studio P1 Voice Recorder polish tests failed with exit code $LASTEXITCODE"
}

& $java --add-modules jdk.httpserver -ea -classpath $runtimeClasspath com.inigmasgames.persistentnpcs.R140NpcAuthoringStudioP1FramePolishTest
if ($LASTEXITCODE -ne 0) {
    throw "R140 NPC Authoring Studio P1 frame-polish tests failed with exit code $LASTEXITCODE"
}

& $java --add-modules jdk.httpserver -ea -classpath $runtimeClasspath com.inigmasgames.persistentnpcs.R141NpcAuthoringStudioP1UxPolishTest
if ($LASTEXITCODE -ne 0) {
    throw "R141 NPC Authoring Studio P1 UX-polish tests failed with exit code $LASTEXITCODE"
}

& $java --add-modules jdk.httpserver -ea -classpath $runtimeClasspath com.inigmasgames.persistentnpcs.R142NpcAuthoringStudioUiLoadAndDefaultAppearanceHotfixTest
if ($LASTEXITCODE -ne 0) {
    throw "R142 NPC Authoring Studio UI-load/default-appearance hotfix tests failed with exit code $LASTEXITCODE"
}

& $java --add-modules jdk.httpserver -ea -classpath $runtimeClasspath com.inigmasgames.persistentnpcs.training.R125OrbisDistillationBlock1Test
if ($LASTEXITCODE -ne 0) {
    throw "R125 Orbis Distillation Block 1 D0-D3 tests failed with exit code $LASTEXITCODE"
}

& $java --add-modules jdk.httpserver -ea -classpath $runtimeClasspath com.inigmasgames.persistentnpcs.training.R126OrbisDistillationBlock2Test
if ($LASTEXITCODE -ne 0) {
    throw "R126 Orbis Distillation Block 2 D4-D5 tests failed with exit code $LASTEXITCODE"
}

& $java --add-modules jdk.httpserver -ea -classpath $runtimeClasspath com.inigmasgames.persistentnpcs.R104NpcMeshPreviewProbeSafetyTest
if ($LASTEXITCODE -ne 0) {
    throw "R104 NPC mesh preview Probe A safety tests failed with exit code $LASTEXITCODE"
}

& $java --add-modules jdk.httpserver -ea -classpath $runtimeClasspath com.inigmasgames.persistentnpcs.R093VoiceProfileDiscoveryTest
if ($LASTEXITCODE -ne 0) {
    throw "R093 deterministic voice-profile discovery tests failed with exit code $LASTEXITCODE"
}

& $java --add-modules jdk.httpserver -ea -classpath $runtimeClasspath com.inigmasgames.persistentnpcs.R094HytaleUuidCompatibilityTest
if ($LASTEXITCODE -ne 0) {
    throw "R094 Hytale UUID compatibility tests failed with exit code $LASTEXITCODE"
}

& $java --add-modules jdk.httpserver -ea -classpath $runtimeClasspath com.inigmasgames.persistentnpcs.R027NativeNpcIntegrationTest
if ($LASTEXITCODE -ne 0) {
    throw "R027.1 native NPC integration tests failed with exit code $LASTEXITCODE"
}

& $java --add-modules jdk.httpserver -ea -classpath $runtimeClasspath com.inigmasgames.persistentnpcs.R028EmotionalVoiceTest
if ($LASTEXITCODE -ne 0) {
    throw "R028 emotional voice tests failed with exit code $LASTEXITCODE"
}

& $java --add-modules jdk.httpserver -ea -classpath $runtimeClasspath com.inigmasgames.persistentnpcs.R029GroundedCognitionTest
if ($LASTEXITCODE -ne 0) {
    throw "R029 grounded cognition tests failed with exit code $LASTEXITCODE"
}

& $java --add-modules jdk.httpserver -ea -classpath $runtimeClasspath com.inigmasgames.persistentnpcs.R030SemanticPerceptionLatencyTest
if ($LASTEXITCODE -ne 0) {
    throw "R030 semantic perception/latency tests failed with exit code $LASTEXITCODE"
}

& $java --add-modules jdk.httpserver -ea -classpath $runtimeClasspath com.inigmasgames.persistentnpcs.R031KnownNpcGuideTest
if ($LASTEXITCODE -ne 0) {
    throw "R031 relationship locator/guide tests failed with exit code $LASTEXITCODE"
}

& $java --add-modules jdk.httpserver -ea -classpath $runtimeClasspath com.inigmasgames.persistentnpcs.R032VoiceHearingArchitectureTest
if ($LASTEXITCODE -ne 0) {
    throw "R032 voice/hearing architecture tests failed with exit code $LASTEXITCODE"
}

& $java --add-modules jdk.httpserver -ea -classpath $runtimeClasspath com.inigmasgames.persistentnpcs.R032DialogueLeakAuditTest
if ($LASTEXITCODE -ne 0) {
    throw "R032.1 dialogue leakage/audit tests failed with exit code $LASTEXITCODE"
}

& $java --add-modules jdk.httpserver -ea -classpath $runtimeClasspath com.inigmasgames.persistentnpcs.R032OperatorTraceSessionTest
if ($LASTEXITCODE -ne 0) {
    throw "R032.2 operator trace session tests failed with exit code $LASTEXITCODE"
}

& $java --add-modules jdk.httpserver -ea -classpath $runtimeClasspath com.inigmasgames.persistentnpcs.R033MemoryHearingTraceTest
if ($LASTEXITCODE -ne 0) {
    throw "R033 memory/hearing/trace tests failed with exit code $LASTEXITCODE"
}

& $java --add-modules jdk.httpserver -ea -classpath $runtimeClasspath com.inigmasgames.persistentnpcs.R034MemoryImportanceTest
if ($LASTEXITCODE -ne 0) {
    throw "R034 memory importance/durability tests failed with exit code $LASTEXITCODE"
}

& $java --add-modules jdk.httpserver -ea -classpath $runtimeClasspath com.inigmasgames.persistentnpcs.R034VoiceActivationReliabilityTest
if ($LASTEXITCODE -ne 0) {
    throw "R034.1 voice activation reliability tests failed with exit code $LASTEXITCODE"
}

& $java --add-modules jdk.httpserver -ea -classpath $runtimeClasspath com.inigmasgames.persistentnpcs.R035DialogueIntelligenceTest
if ($LASTEXITCODE -ne 0) {
    throw "R035 dialogue intelligence/context routing tests failed with exit code $LASTEXITCODE"
}

& $java --add-modules jdk.httpserver -ea -classpath $runtimeClasspath com.inigmasgames.persistentnpcs.R036HardwareAgnosticAiTest
if ($LASTEXITCODE -ne 0) {
    throw "R036 hardware-agnostic AI provider tests failed with exit code $LASTEXITCODE"
}

& $java --add-modules jdk.httpserver -ea -classpath $runtimeClasspath com.inigmasgames.persistentnpcs.R037SelectableLlmProviderTest
if ($LASTEXITCODE -ne 0) {
    throw "R037 selectable Qwen/Nemotron provider tests failed with exit code $LASTEXITCODE"
}

& $java --add-modules jdk.httpserver -ea -classpath $runtimeClasspath com.inigmasgames.persistentnpcs.R038ConstrainedNpcDecisionTest
if ($LASTEXITCODE -ne 0) {
    throw "R038 constrained NPC decision tests failed with exit code $LASTEXITCODE"
}

& $java --add-modules jdk.httpserver -ea -classpath $runtimeClasspath com.inigmasgames.persistentnpcs.R043OrbisRuntimeTest
if ($LASTEXITCODE -ne 0) {
    throw "R043 Orbis authoritative voice runtime tests failed with exit code $LASTEXITCODE"
}

& $java --add-modules jdk.httpserver -ea -classpath $runtimeClasspath com.inigmasgames.persistentnpcs.R044OrbisCognitionOwnershipTest
if ($LASTEXITCODE -ne 0) {
    throw "R044 Orbis cognition/LLM ownership tests failed with exit code $LASTEXITCODE"
}

& $java --add-modules jdk.httpserver -ea -classpath $runtimeClasspath com.inigmasgames.persistentnpcs.R045OrbisSpeechOwnershipTest
if ($LASTEXITCODE -ne 0) {
    throw "R045 Orbis TTS/spatial playback ownership tests failed with exit code $LASTEXITCODE"
}
& $java --add-modules jdk.httpserver -ea -classpath $runtimeClasspath com.inigmasgames.persistentnpcs.orbis.R046OrbisInterruptionTest
if ($LASTEXITCODE -ne 0) {
    throw "R046 Orbis interruption/deferred-state tests failed with exit code $LASTEXITCODE"
}

& $java --add-modules jdk.httpserver -ea -classpath $runtimeClasspath com.inigmasgames.persistentnpcs.orbis.R047OrbisResourceSchedulerTest
if ($LASTEXITCODE -ne 0) {
    throw "R047 Orbis resource scheduling tests failed with exit code $LASTEXITCODE"
}

& $java --add-modules jdk.httpserver -ea -classpath $runtimeClasspath com.inigmasgames.persistentnpcs.orbis.R048OrbisFinalHardeningTest
if ($LASTEXITCODE -ne 0) {
    throw "R048 Orbis final hardening tests failed with exit code $LASTEXITCODE"
}

& $java --add-modules jdk.httpserver -ea -classpath $runtimeClasspath com.inigmasgames.persistentnpcs.R049ReconnectLifecycleTest
if ($LASTEXITCODE -ne 0) {
    throw "R049 reconnect lifecycle tests failed with exit code $LASTEXITCODE"
}

& $java --add-modules jdk.httpserver -ea -classpath $runtimeClasspath com.inigmasgames.persistentnpcs.orbis.R050ReliabilityGroundingLatencyTest
if ($LASTEXITCODE -ne 0) {
    throw "R050 reliability, grounding, and latency tests failed with exit code $LASTEXITCODE"
}

& $java --add-modules jdk.httpserver -ea -classpath $runtimeClasspath com.inigmasgames.persistentnpcs.orbis.R051TraceResourceDiagnosticsTest
if ($LASTEXITCODE -ne 0) {
    throw "R051 trace resource diagnostics tests failed with exit code $LASTEXITCODE"
}

& $java --add-modules jdk.httpserver -ea -classpath $runtimeClasspath com.inigmasgames.persistentnpcs.orbis.R052IngressAdmissionTraceTest
if ($LASTEXITCODE -ne 0) {
    throw "R052 ingress provenance/admission timeline tests failed with exit code $LASTEXITCODE"
}

& $java --add-modules jdk.httpserver -ea -classpath $runtimeClasspath com.inigmasgames.persistentnpcs.orbis.R053CompactResourceTraceTest
if ($LASTEXITCODE -ne 0) {
    throw "R053 compact resource trace tests failed with exit code $LASTEXITCODE"
}

& $java --add-modules jdk.httpserver -ea -classpath $runtimeClasspath com.inigmasgames.persistentnpcs.orbis.R054CascadeResourceRepairTest
if ($LASTEXITCODE -ne 0) {
    throw "R054 cascade/resource repair tests failed with exit code $LASTEXITCODE"
}
& $java --add-modules jdk.httpserver -ea -classpath $runtimeClasspath com.inigmasgames.persistentnpcs.orbis.R055ChatterboxAdmissionRepairTest
if ($LASTEXITCODE -ne 0) {
    throw "R055 Chatterbox admission repair tests failed with exit code $LASTEXITCODE"
}
& $java --add-modules jdk.httpserver -ea -classpath $runtimeClasspath com.inigmasgames.persistentnpcs.orbis.R057WorldWarmupGroundingTest
if ($LASTEXITCODE -ne 0) {
    throw "R057 world warmup/grounding tests failed with exit code $LASTEXITCODE"
}
& $java --add-modules jdk.httpserver -ea -classpath $runtimeClasspath com.inigmasgames.persistentnpcs.orbis.R058AdaptiveReasoningStreamingTest
if ($LASTEXITCODE -ne 0) {
    throw "R058 adaptive reasoning/safe streaming tests failed with exit code $LASTEXITCODE"
}
& $java --add-modules jdk.httpserver -ea -classpath $runtimeClasspath com.inigmasgames.persistentnpcs.conversation.R059StreamingRangeAndOverlapRepairTest
if ($LASTEXITCODE -ne 0) {
    throw "R059 streaming/range/overlap repair tests failed with exit code $LASTEXITCODE"
}
& $java --add-modules jdk.httpserver -ea -classpath $runtimeClasspath com.inigmasgames.persistentnpcs.conversation.R060CanonicalResponseAssemblyTest
if ($LASTEXITCODE -ne 0) {
    throw "R060 canonical response assembly tests failed with exit code $LASTEXITCODE"
}
& $java --add-modules jdk.httpserver -ea -classpath $runtimeClasspath com.inigmasgames.persistentnpcs.ai.R061TraceLagRepairTest
if ($LASTEXITCODE -ne 0) {
    throw "R061 trace-driven lag repair tests failed with exit code $LASTEXITCODE"
}
& $java --add-modules jdk.httpserver -ea -classpath $runtimeClasspath com.inigmasgames.persistentnpcs.llm.R062ProviderWireContractTest
if ($LASTEXITCODE -ne 0) {
    throw "R062 provider wire-contract tests failed with exit code $LASTEXITCODE"
}
& $java --add-modules jdk.httpserver -ea -classpath $runtimeClasspath com.inigmasgames.persistentnpcs.orbis.R063SteadyStateResourceCalibrationTest
if ($LASTEXITCODE -ne 0) {
    throw "R063 steady-state resource calibration tests failed with exit code $LASTEXITCODE"
}
& $java --add-modules jdk.httpserver -ea -classpath $runtimeClasspath com.inigmasgames.persistentnpcs.orbis.R064RapidFireStabilityTest
if ($LASTEXITCODE -ne 0) {
    throw "R064 rapid-fire/provider-cancellation tests failed with exit code $LASTEXITCODE"
}

& $java --add-modules jdk.httpserver -ea -classpath $runtimeClasspath com.inigmasgames.persistentnpcs.R065OrbisLlamaCppPhase1Test
if ($LASTEXITCODE -ne 0) {
    throw "R065 Orbis llama.cpp protocol/manifest tests failed with exit code $LASTEXITCODE"
}

& $java --add-modules jdk.httpserver -ea -classpath $runtimeClasspath com.inigmasgames.persistentnpcs.R066ReadinessHudTest
if ($LASTEXITCODE -ne 0) {
    throw "R066 native readiness HUD tests failed with exit code $LASTEXITCODE"
}

& $java --add-modules jdk.httpserver -ea -classpath $runtimeClasspath com.inigmasgames.persistentnpcs.orbis.R067SustainableOperatingEnvelopeTest
if ($LASTEXITCODE -ne 0) {
    throw "R067 sustainable operating-envelope tests failed with exit code $LASTEXITCODE"
}

& $java --add-modules jdk.httpserver -ea -classpath $runtimeClasspath com.inigmasgames.persistentnpcs.epistemic.R068EpistemicE0Test
if ($LASTEXITCODE -ne 0) {
    throw "R068 epistemic E0 contract tests failed with exit code $LASTEXITCODE"
}

& $java --add-modules jdk.httpserver -ea -classpath $runtimeClasspath com.inigmasgames.persistentnpcs.epistemic.R069EpistemicE1Test
if ($LASTEXITCODE -ne 0) {
    throw "R069 epistemic E1 dialogue/query tests failed with exit code $LASTEXITCODE"
}

& $java --add-modules jdk.httpserver -ea -classpath $runtimeClasspath com.inigmasgames.persistentnpcs.epistemic.R070EpistemicE2Test
if ($LASTEXITCODE -ne 0) {
    throw "R070 epistemic E2 evidence/answer-plan tests failed with exit code $LASTEXITCODE"
}

& $java --add-modules jdk.httpserver -ea -classpath $runtimeClasspath com.inigmasgames.persistentnpcs.epistemic.R071EpistemicE3Test
if ($LASTEXITCODE -ne 0) {
    throw "R071 epistemic E3 authoritative claim-firewall tests failed with exit code $LASTEXITCODE"
}

& $java --add-modules jdk.httpserver -ea -classpath $runtimeClasspath com.inigmasgames.persistentnpcs.epistemic.R072EpistemicE31IntegrationTest
if ($LASTEXITCODE -ne 0) {
    throw "R072 epistemic E3.1 production handoff tests failed with exit code $LASTEXITCODE"
}
& $java --add-modules jdk.httpserver -ea -classpath $runtimeClasspath com.inigmasgames.persistentnpcs.sentinel.R077SentinelS1Test
if ($LASTEXITCODE -ne 0) {
    throw "R077 Sentinel S1 tests failed with exit code $LASTEXITCODE"
}

& $java --add-modules jdk.httpserver -ea -classpath $runtimeClasspath com.inigmasgames.persistentnpcs.sentinel.R078SentinelS2Test
if ($LASTEXITCODE -ne 0) {
    throw "R078 Sentinel S2 tests failed with exit code $LASTEXITCODE"
}
& $java --add-modules jdk.httpserver -ea -classpath $runtimeClasspath com.inigmasgames.persistentnpcs.sentinel.R079SentinelS3Test
if ($LASTEXITCODE -ne 0) {
    throw "R079 Sentinel S3 tests failed with exit code $LASTEXITCODE"
}
& $java --add-modules jdk.httpserver -ea -classpath $runtimeClasspath com.inigmasgames.persistentnpcs.epistemic.R080EpistemicE4Test
if ($LASTEXITCODE -ne 0) {
    throw "R080 epistemic E4 persistence tests failed with exit code $LASTEXITCODE"
}
& $java --add-modules jdk.httpserver -ea -classpath $runtimeClasspath com.inigmasgames.persistentnpcs.epistemic.R081EpistemicE5Test
if ($LASTEXITCODE -ne 0) {
    throw "R081 epistemic E5 hybrid retrieval tests failed with exit code $LASTEXITCODE"
}
& $java --add-modules jdk.httpserver -ea -classpath $runtimeClasspath com.inigmasgames.persistentnpcs.epistemic.R082EpistemicE6Test
if ($LASTEXITCODE -ne 0) {
    throw "R082 epistemic E6 social cognition tests failed with exit code $LASTEXITCODE"
}
& $java --add-modules jdk.httpserver -ea -classpath $runtimeClasspath com.inigmasgames.persistentnpcs.epistemic.R083EpistemicE7Test
if ($LASTEXITCODE -ne 0) {
    throw "R083 epistemic E7 reflection/outcome tests failed with exit code $LASTEXITCODE"
}

& $java --add-modules jdk.httpserver -ea -classpath $runtimeClasspath com.inigmasgames.persistentnpcs.epistemic.R084EpistemicE8Test
if ($LASTEXITCODE -ne 0) {
    throw "R084 epistemic E8 autonomous ReAct/skill tests failed with exit code $LASTEXITCODE"
}

& $java --add-modules jdk.httpserver -ea -classpath $runtimeClasspath com.inigmasgames.persistentnpcs.voice.R087ConnectedGateBRepairTest
if ($LASTEXITCODE -ne 0) {
    throw "R087 connected Gate-B repair tests failed with exit code $LASTEXITCODE"
}

& $java --add-modules jdk.httpserver -ea -classpath $runtimeClasspath com.inigmasgames.persistentnpcs.conversation.R088DualTraceRepairTest
if ($LASTEXITCODE -ne 0) {
    throw "R088 Mara/Lycander dual-trace tests failed with exit code $LASTEXITCODE"
}

& $java --add-modules jdk.httpserver -ea -classpath $runtimeClasspath com.inigmasgames.persistentnpcs.epistemic.R089SystemicEpistemicRecoveryTest
if ($LASTEXITCODE -ne 0) {
    throw "R089 systemic epistemic recovery tests failed with exit code $LASTEXITCODE"
}

& $java --add-modules jdk.httpserver -ea -classpath $runtimeClasspath com.inigmasgames.persistentnpcs.evaluation.R090H0H1EvaluationHarnessTest
if ($LASTEXITCODE -ne 0) {
    throw "R090 H0/H1 evaluation harness tests failed with exit code $LASTEXITCODE"
}

& $java --add-modules jdk.httpserver -ea -classpath $runtimeClasspath com.inigmasgames.persistentnpcs.evaluation.R090H3DiagnosisTest
if ($LASTEXITCODE -ne 0) {
    throw "R090 H3 evaluation diagnosis tests failed with exit code $LASTEXITCODE"
}

& $java --add-modules jdk.httpserver -ea -classpath $runtimeClasspath com.inigmasgames.persistentnpcs.evaluation.R090H4FixVerificationTest
if ($LASTEXITCODE -ne 0) {
    throw "R090 H4 fix verification tests failed with exit code $LASTEXITCODE"
}

& $java --add-modules jdk.httpserver -ea -classpath $runtimeClasspath com.inigmasgames.persistentnpcs.evaluation.R090H5FrozenFixtureTest
if ($LASTEXITCODE -ne 0) {
    throw "R090 H5 frozen fixture tests failed with exit code $LASTEXITCODE"
}

& $java --add-modules jdk.httpserver -ea -classpath $runtimeClasspath com.inigmasgames.persistentnpcs.evaluation.R090H6CampaignTest
if ($LASTEXITCODE -ne 0) { throw "R090 H6 campaign tests failed with exit code $LASTEXITCODE" }

& $java --add-modules jdk.httpserver -ea -classpath $runtimeClasspath com.inigmasgames.persistentnpcs.evaluation.R090H7LearningTest
if ($LASTEXITCODE -ne 0) { throw "R090 H7 learning tests failed with exit code $LASTEXITCODE" }

& $java --add-modules jdk.httpserver -ea -classpath $runtimeClasspath com.inigmasgames.persistentnpcs.evaluation.R090H8MultiAgentTest
if ($LASTEXITCODE -ne 0) { throw "R090 H8 multi-agent tests failed with exit code $LASTEXITCODE" }

& $java --add-modules jdk.httpserver -ea -classpath $runtimeClasspath com.inigmasgames.persistentnpcs.evaluation.R090AuthoritativeRecallRegressionTest
if ($LASTEXITCODE -ne 0) { throw "R090 authoritative recall regression failed with exit code $LASTEXITCODE" }

& $java --add-modules jdk.httpserver -ea -classpath $runtimeClasspath com.inigmasgames.persistentnpcs.evaluation.R090GateCAutomatedReadinessTest
if ($LASTEXITCODE -ne 0) { throw "R090 Gate C automated readiness failed with exit code $LASTEXITCODE" }

& $java --add-modules jdk.httpserver -ea -classpath $runtimeClasspath com.inigmasgames.persistentnpcs.epistemic.R091GateABehaviorHardeningTest
if ($LASTEXITCODE -ne 0) { throw "R091 Gate-A behavior hardening failed with exit code $LASTEXITCODE" }

& $java --add-modules jdk.httpserver -ea -classpath $runtimeClasspath com.inigmasgames.persistentnpcs.evaluation.R090GateBCleanupRegressionTest
if ($LASTEXITCODE -ne 0) { throw "R090 strict Gate-B cleanup regression failed with exit code $LASTEXITCODE" }

& $java --add-modules jdk.httpserver -ea -classpath $runtimeClasspath com.inigmasgames.persistentnpcs.evaluation.R090ProviderZeroTokenRecoveryTest
if ($LASTEXITCODE -ne 0) { throw "R090 zero-token provider recovery regression failed with exit code $LASTEXITCODE" }

& $java --add-modules jdk.httpserver -ea -classpath $runtimeClasspath com.inigmasgames.persistentnpcs.conversation.ConversationMatrixHarness
if ($LASTEXITCODE -ne 0) {
    throw "Orbis conversational hardening Gate 1 matrix failed with exit code $LASTEXITCODE"
}

& $java --add-modules jdk.httpserver -ea -classpath $runtimeClasspath com.inigmasgames.persistentnpcs.R148NpcProfilePagingResistanceTest
if ($LASTEXITCODE -ne 0) {
    throw "R148 paging/resistance tests failed with exit code $LASTEXITCODE"
}

& $java --add-modules jdk.httpserver -ea -classpath $runtimeClasspath com.inigmasgames.persistentnpcs.stats.R149PersistentVanillaStatsTest
if ($LASTEXITCODE -ne 0) { throw "R149 persistent vanilla stats tests failed with exit code $LASTEXITCODE" }

& $java --add-modules jdk.httpserver -ea -classpath $runtimeClasspath com.inigmasgames.persistentnpcs.R150NpcAppearancePolishTest
if ($LASTEXITCODE -ne 0) { throw "R150 appearance polish tests failed with exit code $LASTEXITCODE" }

& $java --add-modules jdk.httpserver -ea -classpath $runtimeClasspath com.inigmasgames.persistentnpcs.R151NpcAppearanceNativeCardsTest
if ($LASTEXITCODE -ne 0) { throw "R151 appearance native cards tests failed with exit code $LASTEXITCODE" }

& $java --add-modules jdk.httpserver -ea -classpath $runtimeClasspath com.inigmasgames.persistentnpcs.R153AppearanceColorCardsTest
if ($LASTEXITCODE -ne 0) { throw "R153 private appearance color cards tests failed with exit code $LASTEXITCODE" }

& $java --add-modules jdk.httpserver -ea -classpath $runtimeClasspath com.inigmasgames.persistentnpcs.R147NpcProfileRepairTest
if ($LASTEXITCODE -ne 0) {
    throw "R147 Profile repair tests failed with exit code $LASTEXITCODE"
}

& $java --add-modules jdk.httpserver -ea -classpath $runtimeClasspath com.inigmasgames.persistentnpcs.R146NpcProfileMainMenuPolishTest
if ($LASTEXITCODE -ne 0) {
    throw "R146 Profile main-menu polish tests failed with exit code $LASTEXITCODE"
}

if ($SkipLive) {
    Write-Host 'All deterministic Persistent NPC tests passed; live local-model tests skipped.'
    exit 0
}

$orbisLlmManifest = Join-Path $projectRoot 'build\orbisllm\orbisllm-windows-x64-cuda.json'
if (Test-Path -LiteralPath $orbisLlmManifest) {
    & $java --add-modules jdk.httpserver -ea -classpath $runtimeClasspath `
        com.inigmasgames.persistentnpcs.R065OrbisLlamaCppPhase1Test $orbisLlmManifest
    if ($LASTEXITCODE -ne 0) {
        throw "R065 real Orbis llama.cpp lifecycle test failed with exit code $LASTEXITCODE"
    }
}

& $java --add-modules jdk.httpserver -ea -classpath $runtimeClasspath com.inigmasgames.persistentnpcs.RealLocalDialogueRegressionTest
if ($LASTEXITCODE -ne 0) {
    throw "Real dialogue regression tests failed with exit code $LASTEXITCODE"
}

& $java --add-modules jdk.httpserver -ea -classpath $runtimeClasspath com.inigmasgames.persistentnpcs.RealLocalGroundingRegressionTest
if ($LASTEXITCODE -ne 0) {
    throw "Real R008 grounding regression failed with exit code $LASTEXITCODE"
}

& $java --add-modules jdk.httpserver -ea -classpath $runtimeClasspath com.inigmasgames.persistentnpcs.RealLocalR010GroundingRegressionTest
if ($LASTEXITCODE -ne 0) {
    throw "Real R010 grounding regression failed with exit code $LASTEXITCODE"
}

& $java --add-modules jdk.httpserver -ea -classpath $runtimeClasspath com.inigmasgames.persistentnpcs.RealLocalR011EnvironmentTest
if ($LASTEXITCODE -ne 0) {
    throw "Real R011 environment grounding failed with exit code $LASTEXITCODE"
}

& $java --add-modules jdk.httpserver -ea -classpath $runtimeClasspath com.inigmasgames.persistentnpcs.RealLocalR012CognitionTest
if ($LASTEXITCODE -ne 0) {
    throw "Real R012 cognition test failed with exit code $LASTEXITCODE"
}

& $java --add-modules jdk.httpserver -ea -classpath $runtimeClasspath com.inigmasgames.persistentnpcs.RealLocalLlmBenchmark
if ($LASTEXITCODE -ne 0) {
    throw "Real local-model benchmark failed with exit code $LASTEXITCODE"
}

& $java --add-modules jdk.httpserver -ea -classpath $runtimeClasspath com.inigmasgames.persistentnpcs.R037QwenNemotronBenchmark
if ($LASTEXITCODE -ne 0) {
    throw "R037 live Qwen/Nemotron A/B benchmark failed with exit code $LASTEXITCODE"
}

& $java --add-modules jdk.httpserver -ea -classpath $runtimeClasspath com.inigmasgames.persistentnpcs.R037LiveProviderSelectionTest
if ($LASTEXITCODE -ne 0) {
    throw "R037 live runtime provider-switch test failed with exit code $LASTEXITCODE"
}

& $java --add-modules jdk.httpserver -ea -classpath $runtimeClasspath com.inigmasgames.persistentnpcs.R038LiveStructuredDecisionTest
if ($LASTEXITCODE -ne 0) {
    throw "R038 live Qwen/Nemotron structured-output test failed with exit code $LASTEXITCODE"
}

& $java --add-modules jdk.httpserver -ea -classpath $runtimeClasspath com.inigmasgames.persistentnpcs.RealLocalActionBenchmark
if ($LASTEXITCODE -ne 0) {
    throw "Real local action benchmark failed with exit code $LASTEXITCODE"
}

Write-Host 'All Persistent NPC Milestone 2 tests passed.'
