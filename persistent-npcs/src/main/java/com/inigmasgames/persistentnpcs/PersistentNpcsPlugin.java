package com.inigmasgames.persistentnpcs;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.event.events.player.PlayerChatEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerInteractEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.event.events.player.AddPlayerToWorldEvent;
import com.hypixel.hytale.server.core.universe.world.events.StartWorldEvent;
import com.hypixel.hytale.server.core.universe.world.events.AllWorldsLoadedEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.io.adapter.PacketAdapters;
import com.hypixel.hytale.server.core.io.adapter.PacketFilter;
import com.hypixel.hytale.server.core.io.adapter.PlayerPacketWatcher;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.inigmasgames.persistentnpcs.action.HytaleNpcActionService;
import com.inigmasgames.persistentnpcs.action.NpcActionRegistry;
import com.inigmasgames.persistentnpcs.api.PersistentNpcsApi;
import com.inigmasgames.persistentnpcs.command.ImmersiveNpcCreateCommand;
import com.inigmasgames.persistentnpcs.command.ImmersiveNpcTraceCommand;
import com.inigmasgames.persistentnpcs.command.ImmersiveNpcUpdateCommand;
import com.inigmasgames.persistentnpcs.command.NativeInventoryProbeCommand;
import com.inigmasgames.persistentnpcs.ui.NpcMeshPreviewSession;
import com.inigmasgames.persistentnpcs.ui.CustomGridDifferentialTelemetry;
import com.inigmasgames.persistentnpcs.command.CognitionInspectorCommand;
import com.inigmasgames.persistentnpcs.command.SentinelOperatorCommand;
import com.inigmasgames.persistentnpcs.compat.NativeNpcCommandCompatibility;
import com.inigmasgames.persistentnpcs.config.ConfigRepository;
import com.inigmasgames.persistentnpcs.config.FrameworkConfig;
import com.inigmasgames.persistentnpcs.ai.AiProviderConfig;
import com.inigmasgames.persistentnpcs.ai.AiProviderConfigRepository;
import com.inigmasgames.persistentnpcs.ai.AiServiceRouter;
import com.inigmasgames.persistentnpcs.ai.AiServiceRouterFactory;
import com.inigmasgames.persistentnpcs.ai.LlmProviderCatalog;
import com.inigmasgames.persistentnpcs.ai.LlmProviderCatalogRepository;
import com.inigmasgames.persistentnpcs.conversation.ConversationContextBuilder;
import com.inigmasgames.persistentnpcs.conversation.ConversationService;
import com.inigmasgames.persistentnpcs.conversation.ConversationSessionManager;
import com.inigmasgames.persistentnpcs.conversation.ConversationGroundingService;
import com.inigmasgames.persistentnpcs.hytale.HytaleConversationBridge;
import com.inigmasgames.persistentnpcs.hytale.HytaleNpcAdapter;
import com.inigmasgames.persistentnpcs.hytale.HytaleItemContentCatalog;
import com.inigmasgames.persistentnpcs.hytale.HytaleRewardCandidates;
import com.inigmasgames.persistentnpcs.hytale.NpcIntelligenceTickSystem;
import com.inigmasgames.persistentnpcs.hytale.NpcRuntimeRegistry;
import com.inigmasgames.persistentnpcs.hytale.RuntimeApiCompatibility;
import com.inigmasgames.persistentnpcs.hytale.ImmersiveNpcRoleService;
import com.inigmasgames.persistentnpcs.llm.LlmProvider;
import com.inigmasgames.persistentnpcs.llm.ConversationRateLimiter;
import com.inigmasgames.persistentnpcs.quest.DynamicQuestDirector;
import com.inigmasgames.persistentnpcs.quest.DynamicQuestStore;
import com.inigmasgames.persistentnpcs.quest.RewardBudget;
import com.inigmasgames.persistentnpcs.quest.RewardResolver;
import com.inigmasgames.persistentnpcs.monster.ImmersiveAgentStore;
import com.inigmasgames.persistentnpcs.autonomy.AutonomyGate;
import com.inigmasgames.persistentnpcs.autonomy.AutonomousEventDirector;
import com.inigmasgames.persistentnpcs.autonomy.AutonomousOpportunity;
import com.inigmasgames.persistentnpcs.autonomy.AffordanceRegistry;
import com.inigmasgames.persistentnpcs.autonomy.HytaleAutonomousCognitionController;
import com.inigmasgames.persistentnpcs.autonomy.NpcCognitionStateStore;
import com.inigmasgames.persistentnpcs.autonomy.AgentOperationStore;
import com.inigmasgames.persistentnpcs.autonomy.NpcSkillLibrary;
import com.inigmasgames.persistentnpcs.autonomy.NpcAutonomousReActService;
import com.inigmasgames.persistentnpcs.background.BackgroundLifeRuntime;
import com.inigmasgames.persistentnpcs.background.BackgroundLifeSimulator;
import com.inigmasgames.persistentnpcs.background.BackgroundLifeStore;
import com.inigmasgames.persistentnpcs.scene.NpcSceneRunner;
import com.inigmasgames.persistentnpcs.scene.NpcSceneService;
import com.inigmasgames.persistentnpcs.event.NpcEventBus;
import com.inigmasgames.persistentnpcs.event.NpcTriggerService;
import com.inigmasgames.persistentnpcs.economy.ObligationStore;
import com.inigmasgames.persistentnpcs.memory.MemoryStore;
import com.inigmasgames.persistentnpcs.perception.NpcPerceptionService;
import com.inigmasgames.persistentnpcs.profile.NpcProfile;
import com.inigmasgames.persistentnpcs.profile.ProfileRepository;
import com.inigmasgames.persistentnpcs.profile.NpcProfileRegistry;
import com.inigmasgames.persistentnpcs.profile.NpcProfileEditorService;
import com.inigmasgames.persistentnpcs.persistence.ImmersiveNpcDataMigration;
import com.inigmasgames.persistentnpcs.relationship.RelationshipStore;
import com.inigmasgames.persistentnpcs.social.NpcSocialAttentionService;
import com.inigmasgames.persistentnpcs.social.GossipStore;
import com.inigmasgames.persistentnpcs.cognition.NpcCognitionService;
import com.inigmasgames.persistentnpcs.cognition.NpcEmotionStore;
import com.inigmasgames.persistentnpcs.cognition.SourcedBeliefStore;
import com.inigmasgames.persistentnpcs.diagnostics.NpcTurnAuditLog;
import com.inigmasgames.persistentnpcs.diagnostics.NpcTraceManager;
import com.inigmasgames.persistentnpcs.sentinel.OrbisDegradationSentinel;
import com.inigmasgames.persistentnpcs.sentinel.SentinelContracts;
import com.inigmasgames.persistentnpcs.sentinel.SentinelOrbisEventObserver;
import com.inigmasgames.persistentnpcs.sentinel.OrbisIncidentRecorder;
import com.inigmasgames.persistentnpcs.sentinel.RegressionCandidateExtractor;
import com.inigmasgames.persistentnpcs.cognition.CognitionTraceStore;
import com.inigmasgames.persistentnpcs.cognition.LatencyBudgetRepository;
import com.inigmasgames.persistentnpcs.cognition.ResponseLatencyTraceStore;
import com.inigmasgames.persistentnpcs.perception.KnownNpcLocatorService;
import com.inigmasgames.persistentnpcs.social.KnownNpcGuideCoordinator;
import com.inigmasgames.persistentnpcs.profile.AppearanceRepository;
import com.inigmasgames.persistentnpcs.home.HomeBehaviorConfig;
import com.inigmasgames.persistentnpcs.home.HomeBehaviorConfigRepository;
import com.inigmasgames.persistentnpcs.home.NpcHomeAnchorStore;
import com.inigmasgames.persistentnpcs.home.NpcHomeBehaviorController;
import com.inigmasgames.persistentnpcs.task.NpcTaskScheduler;
import com.inigmasgames.persistentnpcs.task.NpcTaskStore;
import com.inigmasgames.persistentnpcs.task.NpcRuntimeStateStore;
import com.inigmasgames.persistentnpcs.plan.SharedPlanCoordinator;
import com.inigmasgames.persistentnpcs.plan.SharedPlanStore;
import com.inigmasgames.persistentnpcs.scene.NpcAssignmentStore;
import com.inigmasgames.persistentnpcs.scene.NpcConversationTriggerService;
import com.inigmasgames.persistentnpcs.voice.ChatterboxPerformanceController;
import com.inigmasgames.persistentnpcs.voice.NpcVoiceService;
import com.inigmasgames.persistentnpcs.voice.VoicePreset;
import com.inigmasgames.persistentnpcs.voice.VoicePresetRepository;
import com.inigmasgames.persistentnpcs.voice.VoiceRuntimeConfig;
import com.inigmasgames.persistentnpcs.voice.VoiceRuntimeConfigRepository;
import com.inigmasgames.persistentnpcs.voice.HytaleSpatialVoiceAdapter;
import com.inigmasgames.persistentnpcs.voice.PlayerUtteranceAudienceService;
import com.inigmasgames.persistentnpcs.voice.VoiceInteractionTraceStore;
import com.inigmasgames.persistentnpcs.voice.VoiceCaptureLeaseManager;
import com.inigmasgames.persistentnpcs.voice.NpcVoiceRecordingService;
import com.inigmasgames.persistentnpcs.orbis.HytalePttBoundaryAdapter;
import com.inigmasgames.persistentnpcs.orbis.OrbisAudienceGateway;
import com.inigmasgames.persistentnpcs.orbis.OrbisDiagnostics;
import com.inigmasgames.persistentnpcs.orbis.OrbisRuntime;
import com.inigmasgames.persistentnpcs.orbis.OrbisTurnCoordinator;
import com.inigmasgames.persistentnpcs.orbis.OrbisRuntimeFactory;
import com.inigmasgames.persistentnpcs.orbis.OrbisSpeechCoordinator;
import com.inigmasgames.persistentnpcs.orbis.OrbisResourceConfig;
import com.inigmasgames.persistentnpcs.orbis.OrbisResourceConfigRepository;
import com.inigmasgames.persistentnpcs.orbis.OrbisResourceScheduler;
import com.inigmasgames.persistentnpcs.authoring.NpcAuthoringPermissions;
import com.inigmasgames.persistentnpcs.authoring.NpcAuthoringSessionRegistry;
import com.inigmasgames.persistentnpcs.orbis.OrbisStartupCoordinator;
import java.time.Duration;
import java.time.Instant;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import javax.annotation.Nonnull;

public final class PersistentNpcsPlugin extends JavaPlugin {
    public static final String REVISION = "R152-NPC-APPEARANCE-CATEGORY-RIGS";

    private final AtomicReference<NpcProfile> testProfile = new AtomicReference<>();
    private ProfileRepository profiles;
    private com.inigmasgames.persistentnpcs.profile.NpcInventoryRepository npcInventories;
    private com.inigmasgames.persistentnpcs.stats.NpcStatRuntimeBridge npcStats;
    private NpcProfileRegistry profileRegistry;
    private ConversationSessionManager sessions;
    private ConversationService conversations;
    private ConversationRateLimiter sceneBudget;
    private NpcSceneRunner sceneRunner;
    private ImmersiveAgentStore immersiveAgents;
    private HytaleSpatialVoiceAdapter spatialVoice;
    private BackgroundLifeRuntime backgroundLifeRuntime;
    private ImmersiveNpcRoleService immersiveRoles;
    private NativeNpcCommandCompatibility nativeNpcCommands;
    private NpcTraceManager npcTraces;
    private MemoryStore memoryStore;
    private NpcAutonomousReActService autonomousReact;
    private SourcedBeliefStore sourcedBeliefStore;
    private AiServiceRouter aiServices;
    private OrbisRuntime orbisRuntime;
    private VoiceCaptureLeaseManager voiceCaptureLeases;
    private NpcVoiceRecordingService voiceRecorder;
    private OrbisResourceScheduler resourceScheduler;
    private OrbisStartupCoordinator startupCoordinator;
    private OrbisDegradationSentinel degradationSentinel;
    private OrbisIncidentRecorder incidentRecorder;
    private RegressionCandidateExtractor regressionCandidates;
    private PacketFilter customGridInboundWatcher;
    private PacketFilter customGridOutboundWatcher;
    private final Instant pluginConstructedAt;

    public PersistentNpcsPlugin(@Nonnull JavaPluginInit init) {
        super(init);
        pluginConstructedAt = Instant.now();
    }

    @Override
    protected void setup() {
        Instant setupStartedAt = Instant.now();
        NpcAuthoringPermissions.registerAll();
        RuntimeApiCompatibility runtimeCompatibility = RuntimeApiCompatibility.detect();
        if (!runtimeCompatibility.update6NpcApi()) {
            getLogger().at(Level.SEVERE).log(
                    "IMMERSIVE_AI_INCOMPATIBLE_RUNTIME %s", runtimeCompatibility.blockerMessage());
        }
        Path dataDirectory = ImmersiveNpcDataMigration.resolveAndMigrate(
                getDataDirectory(), message -> getLogger().at(Level.INFO).log("%s", message));
        FrameworkConfig config = new ConfigRepository(dataDirectory).load();
        java.util.function.Consumer<String> frameworkLog =
                message -> getLogger().at(Level.INFO).log("%s", message);
        customGridInboundWatcher = PacketAdapters.registerInbound(
                (PlayerPacketWatcher) CustomGridDifferentialTelemetry::observeInbound);
        customGridOutboundWatcher = PacketAdapters.registerOutbound(
                (PlayerPacketWatcher) CustomGridDifferentialTelemetry::observeOutbound);
        frameworkLog.accept("CUSTOM_GRID_PACKET_WATCH_REGISTERED revision=" + REVISION
                + " scope=ACTIVE_PROBE_10_ONLY observationOnly=true"
                + " packetConsumed=false directions=INBOUND_AND_OUTBOUND");
        profiles = new ProfileRepository(dataDirectory);
        npcInventories = new com.inigmasgames.persistentnpcs.profile.NpcInventoryRepository(profiles);
        profileRegistry = new NpcProfileRegistry(profiles);
        reloadProfile();

        // setup() is the earliest supported Update 6 plugin lifecycle. Construct and trigger
        // world-independent provider work before save-store restoration so it overlaps disk I/O.
        VoiceRuntimeConfig voiceConfig = new VoiceRuntimeConfigRepository(
                dataDirectory).load();
        VoicePresetRepository voicePresets = new VoicePresetRepository(dataDirectory);
        voicePresets.installWorkerScript();
        AiProviderConfig aiProviderConfig = new AiProviderConfigRepository(dataDirectory)
                .load(config, voiceConfig);
        LlmProviderCatalogRepository llmCatalogRepository =
                new LlmProviderCatalogRepository(dataDirectory);
        LlmProviderCatalog llmCatalog = llmCatalogRepository.load(aiProviderConfig.llm());
        aiServices = AiServiceRouterFactory.createSelectable(aiProviderConfig, llmCatalog,
                llmCatalogRepository::select, config, voiceConfig, voicePresets,
                dataDirectory, frameworkLog);
        java.util.List<Path> warmupVoiceReferences = new java.util.ArrayList<>();
        for (NpcProfile profile : profileRegistry.profiles()) {
            try {
                VoicePreset preset = voicePresets.resolve(profile);
                voicePresets.referenceAudio(preset)
                        .filter(VoicePresetRepository::validWave)
                        .ifPresent(warmupVoiceReferences::add);
            } catch (RuntimeException failure) {
                frameworkLog.accept("ORBIS_STARTUP voice conditioning skipped npc="
                        + profile.name() + " reason=" + failure.getMessage());
            }
        }
        OrbisResourceConfigRepository resourceConfigRepository =
                new OrbisResourceConfigRepository(dataDirectory);
        OrbisResourceConfig resourceConfig = resourceConfigRepository.load();
        resourceScheduler = new OrbisResourceScheduler(resourceConfig,
                aiServices::resourceSnapshot, frameworkLog, resourceConfigRepository::savePolicy,
                aiServices::reclaimResources);
        startupCoordinator = new OrbisStartupCoordinator(aiServices,
                warmupVoiceReferences, resourceConfig, frameworkLog);
        resourceScheduler.configureConversationOperatingEnvelope(
                () -> aiServices.conversationOperatingEnvelope(
                        resourceConfig.hytaleGpuSafetyReserveMiB()),
                startupCoordinator.readiness());
        startupCoordinator.whenReady(resourceScheduler::startConversationOperatingEnvelope);
        startupCoordinator.stage("PLUGIN_CONSTRUCTED", "none",
                Duration.between(pluginConstructedAt, setupStartedAt).toMillis(), "N/A",
                "JavaPlugin constructed before setup callback");
        startupCoordinator.stage("PLUGIN_SETUP", "PLUGIN_CONSTRUCTED",
                Duration.between(setupStartedAt, Instant.now()).toMillis(), "N/A",
                "earliest supported Update 6 plugin lifecycle");
        startupCoordinator.trigger("PluginSetupEvent");

        Instant dataRestoreStartedAt = Instant.now();

        RelationshipStore relationships = new RelationshipStore(dataDirectory);
        relationships.load();
        int authoredRelationships = relationships.importAuthored(
                profiles.relationshipSources(), profileRegistry);
        MemoryStore memories = new MemoryStore(dataDirectory, config.maxMemoryRecords());
        memories.load();
        memoryStore = memories;
        SourcedBeliefStore sourcedBeliefs = new SourcedBeliefStore(dataDirectory);
        sourcedBeliefs.load();
        sourcedBeliefStore = sourcedBeliefs;
        NpcCognitionStateStore cognitionStates = new NpcCognitionStateStore(dataDirectory);
        cognitionStates.load();
        BackgroundLifeStore backgroundLifeStore = new BackgroundLifeStore(dataDirectory);
        backgroundLifeStore.load();
        AgentOperationStore agentOperations = new AgentOperationStore(dataDirectory);
        agentOperations.load();
        GossipStore gossip = new GossipStore(dataDirectory);
        gossip.load();
        ObligationStore obligations = new ObligationStore(dataDirectory);
        obligations.load();
        NpcTaskStore taskStore = new NpcTaskStore(dataDirectory);
        taskStore.load();
        SharedPlanStore sharedPlans = new SharedPlanStore(dataDirectory);
        sharedPlans.load();
        NpcAssignmentStore assignments = new NpcAssignmentStore(dataDirectory);
        assignments.load();
        NpcConversationTriggerService npcConversationTriggers =
                new NpcConversationTriggerService(assignments);
        DynamicQuestStore questStore = new DynamicQuestStore(dataDirectory);
        questStore.load();
        NpcRuntimeStateStore runtimeStates = new NpcRuntimeStateStore(dataDirectory);
        runtimeStates.load();
        sessions = new ConversationSessionManager(Duration.ofSeconds(config.sessionIdleSeconds()),
                memories);
        NpcRuntimeRegistry runtimes = new NpcRuntimeRegistry();
        NpcPerceptionService perception = new NpcPerceptionService(runtimes, profileRegistry);
        NpcActionRegistry actionRegistry = new NpcActionRegistry();
        NpcEventBus eventBus = new NpcEventBus();
        frameworkLog.accept("Imported " + authoredRelationships
                + " authored NPC relationship record(s) into the existing store.");
        NpcEmotionStore emotionStore = new NpcEmotionStore(dataDirectory);
        emotionStore.load();
        HytaleAutonomousCognitionController autonomousCognition =
                new HytaleAutonomousCognitionController(cognitionStates, memories,
                        new AffordanceRegistry(), frameworkLog, emotionStore,
                        obligations, agentOperations);
        BackgroundLifeSimulator backgroundLife = new BackgroundLifeSimulator(
                backgroundLifeStore, memories, frameworkLog);
        NpcHomeAnchorStore homeAnchors = new NpcHomeAnchorStore(dataDirectory);
        homeAnchors.load();
        HomeBehaviorConfig homeConfig = new HomeBehaviorConfigRepository(
                dataDirectory).load();
        NpcHomeBehaviorController homeBehavior = new NpcHomeBehaviorController(
                homeAnchors, taskStore, homeConfig, frameworkLog);
        AppearanceRepository appearances = new AppearanceRepository(
                dataDirectory, frameworkLog);
        NpcTriggerService triggers = new NpcTriggerService(dataDirectory, memories);
        eventBus.register(triggers::onEvent);
        HytaleNpcActionService actionService = new HytaleNpcActionService(
                runtimes, taskStore, eventBus, relationships, obligations,
                frameworkLog, homeBehavior);
        actionService.registerDefaults(actionRegistry);
        new KnownNpcGuideCoordinator(relationships, agentOperations, sharedPlans,
                taskStore, memories).register(actionRegistry);
        new SharedPlanCoordinator(sharedPlans, taskStore, memories).register(actionRegistry);
        DynamicQuestDirector questDirector = new DynamicQuestDirector(questStore,
                new RewardResolver(new RewardBudget(4, 30)), taskStore, memories, relationships);
        eventBus.register(questDirector::onEvent);
        ConversationContextBuilder contextBuilder = new ConversationContextBuilder(
                relationships, memories, taskStore, questStore, sharedPlans,
                config.recentMemoryCount());
        startupCoordinator.dataReady("profiles=" + profileRegistry.profiles().size()
                        + "; relationships/memory/belief/task indexes restored",
                Duration.between(dataRestoreStartedAt, Instant.now()).toMillis(),
                sourcedBeliefs.restorationStats().snapshotHit());
        LlmProvider provider = aiServices.languageModel();
        CognitionTraceStore cognitionTraces = new CognitionTraceStore();
        ResponseLatencyTraceStore responseLatency = new ResponseLatencyTraceStore(
                new LatencyBudgetRepository(dataDirectory).load());
        npcTraces = new NpcTraceManager(profiles, frameworkLog,
                aiServices::runtimeResourceDiagnostics);
        java.util.function.Consumer<com.inigmasgames.persistentnpcs.sentinel.SentinelEvent>
                sentinelEvents = event -> {
                    if (event.npcId() == null) return;
                    com.google.gson.JsonObject json = new com.google.gson.JsonObject();
                    json.addProperty("at", event.at().toString());
                    json.addProperty("event", event.event());
                    json.addProperty("invariantId", event.invariantId());
                    json.addProperty("verdict", event.verdict().name());
                    json.addProperty("severity", event.severity().name());
                    json.addProperty("confidence", event.confidence().name());
                    json.addProperty("scopeKey", event.scopeKey());
                    json.addProperty("reasonCode", event.reasonCode());
                    json.addProperty("failureSignature", event.failureSignature());
                    json.addProperty("occurrenceCount", event.occurrenceCount());
                    json.addProperty("evaluationMicros", event.evaluationMicros());
                    json.add("correlationIds", com.inigmasgames.persistentnpcs.json.JsonFiles
                            .GSON.toJsonTree(event.correlationIds()));
                    npcTraces.record(event.npcId(), json);
                };
        degradationSentinel = new OrbisDegradationSentinel(
                SentinelContracts.SentinelMode.ENFORCE, sentinelEvents);
        incidentRecorder = new OrbisIncidentRecorder(dataDirectory, frameworkLog);
        regressionCandidates = new RegressionCandidateExtractor(dataDirectory, REVISION,
                frameworkLog, sentinelEvents);
        incidentRecorder.setCandidateExtractor(regressionCandidates);
        degradationSentinel.setIncidentRecorder(incidentRecorder);
        degradationSentinel.setRegressionCandidates(regressionCandidates);
        sourcedBeliefs.setDegradationSentinel(degradationSentinel);
        resourceScheduler.setDegradationSentinel(degradationSentinel);
        NpcTurnAuditLog turnAuditLog = new NpcTurnAuditLog(npcTraces);
        NpcCognitionService cognition = new NpcCognitionService(
                relationships, taskStore, emotionStore, profileRegistry, memories,
                obligations, sharedPlans, agentOperations, sourcedBeliefs,
                cognitionTraces, responseLatency);
        cognition.configureReflectionScheduling(resourceScheduler);
        cognition.configureReflectionDiagnostics(frameworkLog);
        NpcSkillLibrary skillLibrary = new NpcSkillLibrary();
        autonomousReact = new NpcAutonomousReActService(skillLibrary, actionRegistry,
                agentOperations, cognition, () -> !resourceScheduler.conversationServiceable());
        frameworkLog.accept("E8 autonomous skill library ready skills="
                + skillLibrary.all().size()
                + " execution=validated-action-result-react scheduling=Hytale-first");
        KnownNpcLocatorService knownNpcLocator = new KnownNpcLocatorService(
                profileRegistry, relationships, runtimes);
        VoicePreset maraVoice = voicePresets.loadMaraPreset();
        NpcVoiceService voice = new NpcVoiceService(voicePresets,
                new ChatterboxPerformanceController(), frameworkLog);
        frameworkLog.accept("Mara voice preset loaded id=" + maraVoice.id()
                + " provider=" + maraVoice.provider() + " reference="
                + voicePresets.referenceAudio(maraVoice).map(java.nio.file.Path::toString)
                        .orElse("missing; using Chatterbox built-in fallback when runtime exists"));
        NpcSocialAttentionService attention = new NpcSocialAttentionService(
                profileRegistry, sessions, runtimes, frameworkLog,
                npcId -> taskStore.activeFor(npcId).stream().anyMatch(task -> switch (
                        task.type().toUpperCase(java.util.Locale.ROOT)) {
                    case "FOLLOW_PLAYER", "GO_TO", "PATROL", "WANDER", "FLEE",
                            "ESCORT", "SEARCH_WITH_PLAYER", "GO_TO_LOCATION",
                            "FETCH_ITEM", "FETCH_PERSON", "DELIVER_ITEM",
                            "DELIVER_MESSAGE", "WORK_SHIFT", "RETURN_HOME",
                            "BRING_ITEM", "CRAFT_FOR_PLAYER", "ATTACK", "DEFEND",
                            "CEASE_COMBAT", "CRAFT", "COOK", "PROCESS", "REPAIR",
                            "DELIVER_CRAFTED_ITEM", "GUIDE_PLAYER_TO_NPC" -> true;
                    default -> false;
                }), voiceConfig.effectiveConversationListenRadius(),
                voiceConfig.effectiveRemoteHailRadius());
        VoiceInteractionTraceStore voiceTraces = new VoiceInteractionTraceStore();
        PlayerUtteranceAudienceService utteranceAudience =
                new PlayerUtteranceAudienceService(profileRegistry, runtimes, sessions,
                        relationships, memories, sourcedBeliefs, turnAuditLog,
                        voiceConfig, frameworkLog);
        spatialVoice = new HytaleSpatialVoiceAdapter(voiceConfig, frameworkLog);
        conversations = OrbisRuntimeFactory.createConversation(
                new OrbisRuntimeFactory.ConversationComposition(contextBuilder, provider,
                        relationships, memories, actionRegistry, perception, 1200,
                        message -> getLogger().at(Level.INFO).log("%s", message),
                        new ConversationRateLimiter(
                                config.effectivePerPlayerRequestsPerMinute()),
                        new ConversationGroundingService(new HytaleItemContentCatalog()),
                        cognition, attention, knownNpcLocator));
        conversations.setTurnAuditLog(turnAuditLog);
        conversations.setDegradationSentinel(degradationSentinel);
        frameworkLog.accept("NPC_OPERATOR_TRACE_READY active=false storage=profile-local");

        sceneBudget = new ConversationRateLimiter(config.npcToNpcRequestsPerMinute() == null
                ? 4 : Math.max(1, config.npcToNpcRequestsPerMinute()));
        sceneRunner = new NpcSceneRunner(new NpcSceneService(4, 120), provider,
                sceneBudget, relationships, gossip, memories, voice, null, agentOperations,
                resourceScheduler);
        PersistentNpcsApi.initialize(new PersistentNpcsApi(
                actionRegistry, triggers, eventBus, questDirector, sharedPlans,
                assignments, npcConversationTriggers, sceneRunner));
        frameworkLog.accept("NPC_INTELLIGENCE_READY npcSpeech=structured-direct-text"
                + " sharedPlans=" + sharedPlans.path()
                + " deterministicConversationTriggers="
                + npcConversationTriggers.getClass().getSimpleName());
        immersiveAgents = new ImmersiveAgentStore(dataDirectory);
        immersiveAgents.load();
        AutonomousEventDirector autonomous = new AutonomousEventDirector(
                new AutonomyGate(config.autonomousRequestsPerMinute() == null
                        ? 6 : Math.max(0, config.autonomousRequestsPerMinute()),
                        config.perNpcAutonomyCooldownSeconds() == null
                                ? 60 : config.perNpcAutonomyCooldownSeconds()), 5);
        eventBus.register(event -> autonomous.evaluate(new AutonomousOpportunity(event,
                        event.facts().getOrDefault("unresolvedNeed", ""),
                        Boolean.parseBoolean(event.facts().getOrDefault(
                                "unfinishedBusiness", "false")),
                        Boolean.parseBoolean(event.facts().getOrDefault("usefulGossip", "false")),
                        Boolean.parseBoolean(event.facts().getOrDefault("inDanger", "false")),
                        Boolean.parseBoolean(event.facts().getOrDefault(
                                "scheduleProblem", "false")),
                        safeInteger(event.facts().get("relationshipMotivation")),
                        splitCsv(event.facts().get("eligibleActions")),
                        splitCsv(event.facts().get("availableQuestTypes"))))
                .ifPresent(intent -> getLogger().at(Level.INFO).log(
                        "Autonomous opportunity accepted: %s", intent)));

        HytaleNpcAdapter npcAdapter = new HytaleNpcAdapter(
                runtimes, testProfile::get, appearances, frameworkLog,
                runtimeCompatibility, homeBehavior, npcInventories);
        immersiveRoles = ImmersiveNpcRoleService.update6(
                dataDirectory, profileRegistry, frameworkLog);
        npcStats = new com.inigmasgames.persistentnpcs.stats.NpcStatRuntimeBridge(
                new com.inigmasgames.persistentnpcs.stats.NpcStatStateRepository(profiles, frameworkLog),
                profileRegistry, immersiveRoles, runtimes, frameworkLog);
        npcAdapter.configurePersistentStats(immersiveRoles, npcStats);
        NpcTaskScheduler taskScheduler = new NpcTaskScheduler(
                taskStore, memories, actionService::resumeTask, eventBus, frameworkLog,
                runtimes, agentOperations, sharedPlans);
        if (runtimeCompatibility.update6NpcApi()) {
            getEntityStoreRegistry().registerSystem(new com.inigmasgames.persistentnpcs.stats.NpcStatHydrationSystem(npcStats));
            getEntityStoreRegistry().registerSystem(new com.inigmasgames.persistentnpcs.stats.NpcStatCheckpointSystem(npcStats));
            getEntityStoreRegistry().registerSystem(new com.inigmasgames.persistentnpcs.stats.NpcStatRemovalCaptureSystem(npcStats));
            getEntityStoreRegistry().registerSystem(new NpcIntelligenceTickSystem(
                    profileRegistry, attention, taskScheduler, spatialVoice,
                    runtimes, homeBehavior, appearances, autonomousCognition,
                    backgroundLife, immersiveRoles,
                    value -> aiServices.observeServerFrame((float) value)));
        }
        backgroundLifeRuntime = new BackgroundLifeRuntime(testProfile::get, runtimes,
                backgroundLifeStore, backgroundLife, frameworkLog);
        HytaleConversationBridge bridge = new HytaleConversationBridge(profileRegistry,
                sessions, conversations, npcAdapter, config.maxPlayerMessageCharacters(),
                throwable -> getLogger().at(Level.WARNING).withCause(throwable)
                        .log("Persistent NPC conversation failed"),
                attention, voice, spatialVoice, utteranceAudience, voiceTraces,
                voiceConfig.effectiveNpcSpeechMaxRadius());
        bridge.setConversationalReadiness(
                () -> startupCoordinator.ready()
                        && resourceScheduler.conversationServiceable(),
                () -> startupCoordinator.summary() + "\n"
                        + resourceScheduler.operatingEnvelopeSummary());
        // Autonomous dialogue must not become the first implicit TTS/model warmup.
        startupCoordinator.whenReady(() ->
                attention.setCuriosityConsumer(bridge::initiateCuriosity));
        OrbisDiagnostics orbisDiagnostics = new OrbisDiagnostics();
        orbisDiagnostics.subscribe(npcTraces::recordOrbis);
        orbisDiagnostics.subscribe(voiceTraces::observeOrbis);
        orbisDiagnostics.subscribe(new SentinelOrbisEventObserver(degradationSentinel));
        HytalePttBoundaryAdapter pttBoundary = new HytalePttBoundaryAdapter(
                voiceConfig.effectiveUtteranceGapMillis());
        OrbisAudienceGateway orbisAudience = new OrbisAudienceGateway() {
            @Override public com.inigmasgames.persistentnpcs.voice.SttSemanticCorrector.Correction
                    correctTranscript(UUID playerId, String raw) {
                return utteranceAudience.correctTranscript(playerId, raw);
            }
            @Override public java.util.concurrent.CompletableFuture<
                    PlayerUtteranceAudienceService.Resolution> resolve(
                    com.inigmasgames.persistentnpcs.voice.TranscribedPlayerUtterance value) {
                return bridge.resolveVoiceAudience(value);
            }

            @Override public java.util.concurrent.CompletableFuture<java.util.Map<String, String>>
                    prefetch(UUID playerId, UUID worldId) {
                return bridge.prefetchVoiceContext(playerId, worldId);
            }
        };
        voiceCaptureLeases = new VoiceCaptureLeaseManager(frameworkLog);
        OrbisTurnCoordinator orbisCoordinator = OrbisRuntimeFactory.create(
                new OrbisRuntimeFactory.Composition(
                        aiServices.authoritativeSpeechToText(), orbisAudience, bridge,
                        new OrbisSpeechCoordinator(profileRegistry, voice,
                                aiServices.authoritativeTextToSpeech(), spatialVoice,
                                responseLatency, frameworkLog, resourceScheduler),
                        attention::hasPotentialListener, aiServices::pinLanguageModel,
                        resourceScheduler, orbisDiagnostics,
                        pttBoundary.packetRunReleaseMillis(), 3_000, 30_000,
                        frameworkLog));
        orbisRuntime = new OrbisRuntime(
                com.hypixel.hytale.server.core.modules.voice.VoiceModule.get(),
                orbisCoordinator, resourceScheduler, voiceCaptureLeases);
        voiceRecorder = new NpcVoiceRecordingService(
                com.hypixel.hytale.server.core.modules.voice.VoiceModule.get(),
                voiceCaptureLeases, voicePresets, aiServices, frameworkLog);
        regressionCandidates.setIdleGate(() -> startupCoordinator.ready()
                && resourceScheduler.conversationServiceable()
                && resourceScheduler.snapshot().activeJobs() == 0
                && resourceScheduler.snapshot().queueDepth() == 0);
        bridge.setOrbisRuntime(orbisRuntime);
        attention.setFocusLostConsumer((npcId, playerId) -> {
            OrbisRuntime runtime = orbisRuntime;
            if (runtime != null) runtime.conversationFocusLost(npcId, playerId);
        });
        getEventRegistry().registerGlobal(StartWorldEvent.class, event -> {
            startupCoordinator.lifecycle("StartWorldEvent", event.getWorld()
                    .getWorldConfig().getUuid().toString());
            startupCoordinator.trigger("StartWorldEvent");
        });
        getEventRegistry().registerGlobal(AllWorldsLoadedEvent.class, event -> {
            npcStats.initializeUnspawned();
            startupCoordinator.lifecycle("AllWorldsLoadedEvent", "all-worlds-loaded");
            startupCoordinator.trigger("AllWorldsLoadedEvent");
            startupCoordinator.worldReady("all authoritative worlds loaded");
        });
        getEventRegistry().registerGlobal(AddPlayerToWorldEvent.class, event -> {
            startupCoordinator.lifecycle("AddPlayerToWorldEvent", event.getWorld()
                    .getWorldConfig().getUuid().toString());
            startupCoordinator.trigger("AddPlayerToWorldEvent");
            startupCoordinator.entityBindingReady("player/world binding available; focused NPC "
                    + "binding is resolved lazily without cold provider work");
        });
        getEventRegistry().registerGlobal(
                com.hypixel.hytale.server.core.universe.world.events.RemoveWorldEvent.class,
                event -> {
                    NpcAuthoringSessionRegistry.shared().closeAll();
                    NpcMeshPreviewSession.closeAll();
                    orbisRuntime.worldUnloaded(event.getWorld().getWorldConfig().getUuid());
                });
        getEventRegistry().registerGlobal(
                com.hypixel.hytale.server.core.event.events.player.DrainPlayerFromWorldEvent.class,
                event -> {
                    PlayerRef leaving = event.getHolder().getComponent(PlayerRef.getComponentType());
                    if (leaving != null) {
                        NpcAuthoringSessionRegistry.shared().closeForViewer(leaving.getUuid());
                        NpcMeshPreviewSession.close(leaving.getUuid());
                    }
                });
        frameworkLog.accept("ORBIS_RUNTIME_READY authoritative=capture,stt,audience,"
                + "cognition,llm,npc-decision,canonical-commit,tts,opus,spatial-playback,"
                + "conversation-floor,barge-in,delivery-provenance,deferred-topic"
                + ",resource-admission"
                + " legacyFallback=false pttBoundary=HYTALE_PACKET_RUN"
                + " releaseMillis=" + pttBoundary.packetRunReleaseMillis()
                + " resourcePolicy=" + resourceConfig.policy());

        NpcProfileEditorService profileEditor = new NpcProfileEditorService(
                profiles, profileRegistry, appearances, npcInventories, voicePresets,
                new com.inigmasgames.persistentnpcs.profile.NpcProfileAuthoringService(
                        profiles, profileRegistry, frameworkLog),
                new com.inigmasgames.persistentnpcs.profile.NpcProfileGenerationService(
                        aiServices::pinLanguageModel, resourceScheduler, frameworkLog),
                frameworkLog);
        nativeNpcCommands = new NativeNpcCommandCompatibility(frameworkLog);
        profileEditor.configurePersistentStats(npcStats);
        nativeNpcCommands.install(
                new ImmersiveNpcCreateCommand(profileEditor, profileRegistry,
                        immersiveRoles, npcAdapter, bridge,
                        voiceRecorder, runtimeCompatibility::blockerMessage, frameworkLog),
                new ImmersiveNpcUpdateCommand(profileEditor, profileRegistry,
                        immersiveRoles, npcAdapter, bridge,
                        voiceRecorder, runtimeCompatibility::blockerMessage, frameworkLog),
                new ImmersiveNpcTraceCommand(profileRegistry, npcTraces));
        getCommandRegistry().registerCommand(
                new CognitionInspectorCommand(profileRegistry, cognitionTraces,
                        responseLatency, agentOperations, voiceTraces, aiServices,
                        orbisRuntime, npcTraces, degradationSentinel));
        getCommandRegistry().registerCommand(new NativeInventoryProbeCommand(
                profileRegistry, npcAdapter, npcInventories, frameworkLog));
        getCommandRegistry().registerCommand(new SentinelOperatorCommand(
                degradationSentinel, incidentRecorder, regressionCandidates));
        if (runtimeCompatibility.update6NpcApi()) {
            getEventRegistry().registerGlobal(PlayerChatEvent.class, bridge::handleChat);
        }
        // Update 6 still exposes this event as deprecated; ordinary nearby chat remains
        // available even when a client does not emit the interaction event.
        if (runtimeCompatibility.update6NpcApi()) {
            getEventRegistry().registerGlobal(PlayerInteractEvent.class, bridge::handleInteract);
        }
        getEventRegistry().registerGlobal(PlayerDisconnectEvent.class,
                event -> {
                    UUID playerId = event.getPlayerRef().getUuid();
                    if (orbisRuntime != null) orbisRuntime.playerDisconnected(playerId);
                    if (voiceRecorder != null) voiceRecorder.closeForPlayer(playerId);
                    bridge.disconnected(playerId);
                    npcTraces.disconnect(playerId);
                    NpcMeshPreviewSession.close(playerId);
                    CustomGridDifferentialTelemetry.deactivate(
                            playerId, "PLAYER_DISCONNECTED");
                });
        getEventRegistry().registerGlobal(PlayerReadyEvent.class, event -> {
            startupCoordinator.lifecycle("PlayerReadyEvent",
                    Integer.toString(event.getReadyId()));
            Ref<EntityStore> ref = event.getPlayerRef();
            PlayerRef playerRef = ref.getStore().getComponent(
                    ref, PlayerRef.getComponentType());
            if (playerRef != null) {
                event.getPlayer().getHudManager().addCustomHud(
                        playerRef, new PersistentNpcsHud(
                                playerRef, startupCoordinator.readiness()));
                String readyMessage = runtimeCompatibility.update6NpcApi()
                        ? "Immersive AI voice setup: in Hytale Audio settings choose "
                                + "Voice Input Mode = Push to Talk and bind Push to Talk = E. "
                                + "The server cannot inspect or change this client keybind."
                        : runtimeCompatibility.blockerMessage();
                playerRef.sendMessage(com.hypixel.hytale.server.core.Message.raw(readyMessage));
            }
        });
        CompletableFuture.runAsync(() -> startupCoordinator.optionalBackgroundReady(
                "reflection, background/dormant autonomy, diagnostics, and secondary caches"));
    }

    @Override
    protected void start() {
        if (immersiveRoles != null) {
            immersiveRoles.registerAll();
        }
        getLogger().at(Level.INFO).log(
                "Immersive AI %s started. Test NPC=%s (%s).",
                REVISION, testProfile.get().name(), testProfile.get().id());
    }

    @Override
    protected void shutdown() {
        if (npcStats != null) {
            try { npcStats.close(); }
            catch (RuntimeException failure) { getLogger().at(Level.SEVERE).withCause(failure).log("NPC_STATS_SHUTDOWN_FAILED"); }
        }
        if (customGridInboundWatcher != null) {
            PacketAdapters.deregisterInbound(customGridInboundWatcher);
            customGridInboundWatcher = null;
        }
        if (customGridOutboundWatcher != null) {
            PacketAdapters.deregisterOutbound(customGridOutboundWatcher);
            customGridOutboundWatcher = null;
        }
        NpcMeshPreviewSession.closeAll();
        if (regressionCandidates != null) {
            regressionCandidates.close();
        }
        if (incidentRecorder != null) {
            incidentRecorder.close();
        }
        if (nativeNpcCommands != null) {
            nativeNpcCommands.close();
        }
        if (npcTraces != null) {
            npcTraces.close();
        }
        if (voiceRecorder != null) {
            voiceRecorder.close();
        }
        if (orbisRuntime != null) {
            orbisRuntime.close();
        }
        if (spatialVoice != null) {
            spatialVoice.close();
        }
        if (startupCoordinator != null) {
            startupCoordinator.close();
        }
        if (aiServices != null) {
            aiServices.close();
        }
        if (conversations != null) {
            conversations.shutdown();
        }
        if (sessions != null) {
            sessions.clear();
        }
        if (sceneBudget != null) {
            sceneBudget.close();
        }
        if (backgroundLifeRuntime != null) {
            backgroundLifeRuntime.close();
        }
        if (resourceScheduler != null) {
            resourceScheduler.close();
        }
        if (memoryStore != null) {
            memoryStore.flush();
        }
        if (sourcedBeliefStore != null) {
            sourcedBeliefStore.close();
        }
        if (npcInventories != null) {
            npcInventories.close();
        }
        PersistentNpcsApi.shutdown();
    }

    private void reloadProfile() {
        profileRegistry.load();
        testProfile.set(profileRegistry.defaultProfile());
    }

    private static java.util.List<String> splitCsv(String value) {
        return value == null || value.isBlank() ? java.util.List.of()
                : java.util.Arrays.stream(value.split(",")).map(String::strip)
                        .filter(item -> !item.isBlank()).toList();
    }

    private static int safeInteger(String value) {
        try {
            return value == null ? 0 : Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }
}
