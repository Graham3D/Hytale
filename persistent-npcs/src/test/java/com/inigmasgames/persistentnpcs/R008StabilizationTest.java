package com.inigmasgames.persistentnpcs;

import com.inigmasgames.persistentnpcs.conversation.ContentCatalog;
import com.inigmasgames.persistentnpcs.conversation.ContentValidationResult;
import com.inigmasgames.persistentnpcs.conversation.ContentValidationStatus;
import com.inigmasgames.persistentnpcs.conversation.ConversationContextBuilder;
import com.inigmasgames.persistentnpcs.conversation.ConversationGroundingService;
import com.inigmasgames.persistentnpcs.conversation.ConversationSession;
import com.inigmasgames.persistentnpcs.conversation.ConversationService;
import com.inigmasgames.persistentnpcs.conversation.MinimalWorldContext;
import com.inigmasgames.persistentnpcs.hytale.HytaleConversationBridge;
import com.inigmasgames.persistentnpcs.llm.LlmLatency;
import com.inigmasgames.persistentnpcs.llm.LlmProvider;
import com.inigmasgames.persistentnpcs.llm.LlmProviderStatus;
import com.inigmasgames.persistentnpcs.llm.LlmRequest;
import com.inigmasgames.persistentnpcs.llm.ConversationRateLimiter;
import com.inigmasgames.persistentnpcs.llm.LlmResult;
import com.inigmasgames.persistentnpcs.memory.MemoryStore;
import com.inigmasgames.persistentnpcs.perception.NpcPerceptionSnapshot;
import com.inigmasgames.persistentnpcs.perception.PerceivedItem;
import com.inigmasgames.persistentnpcs.profile.NpcProfile;
import com.inigmasgames.persistentnpcs.relationship.RelationshipStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

public final class R008StabilizationTest {
    private R008StabilizationTest() { }

    public static void main(String[] args) throws Exception {
        verifyPrivatePlayerEcho();
        verifyGroundedDesireInvalidationAndRecovery();
        verifyUnknownClaimAndHeldItemValidation();
        System.out.println("Persistent NPC R008 stabilization tests passed.");
    }

    private static void verifyPrivatePlayerEcho() {
        String exact = "  Do you want this potion?  ";
        assert HytaleConversationBridge.formatPlayerEcho("Graham", exact)
                .equals("Graham: " + exact);
    }

    private static void verifyGroundedDesireInvalidationAndRecovery() throws Exception {
        Path directory = Files.createTempDirectory("persistent-npc-r008-");
        try {
            UUID npcId = UUID.randomUUID();
            UUID playerId = UUID.randomUUID();
            NpcProfile profile = profile(npcId);
            RelationshipStore relationships = new RelationshipStore(directory);
            relationships.load();
            MemoryStore memories = new MemoryStore(directory, 100);
            memories.load();
            ConversationContextBuilder builder = new ConversationContextBuilder(
                    relationships, memories, 6);
            List<LlmRequest> requests = new CopyOnWriteArrayList<>();
            List<String> logs = new CopyOnWriteArrayList<>();
            LlmProvider repeatingProvider = provider(requests, "I'd like a drink.");
            ContentCatalog catalog = (thing, perception) ->
                    singular(thing).equals("drink")
                            ? new ContentValidationResult(thing,
                                    ContentValidationStatus.NOT_FOUND, List.of(),
                                    "no matching item/category exists in test registry")
                            : ContentValidationResult.unknown(thing, "not covered by test registry");
            ConversationService service = new ConversationService(builder, repeatingProvider,
                    relationships, memories, null, null, 1200, logs::add,
                    new ConversationRateLimiter(120),
                    new ConversationGroundingService(catalog));
            ConversationSession session = new ConversationSession(
                    UUID.randomUUID(), npcId, playerId, Instant.now());
            session.appendTurn("What would you like?", "I'd like a drink.", Instant.now());

            String[] messages = {
                    "Drinks don't exist in Hytale. What would you like instead?",
                    "Anything else?", "Are you sure?", "Choose again.",
                    "What would work?", "Tell me your alternative."
            };
            for (String message : messages) {
                var outcome = service.converse(session, profile, message,
                        new MinimalWorldContext("Hytale", 0, 64, 0)).join();
                assert !ConversationGroundingService.extractDesire(outcome.dialogue())
                        .equals("drink") : "Invalid drink desire repeated: " + outcome.dialogue();
            }

            String prompt = requests.getFirst().messages().getFirst().content();
            assert prompt.indexOf("CURRENT PLAYER MESSAGE")
                    < prompt.indexOf("CURRENT_WORLD_STATE (authoritative");
            assert prompt.indexOf("CURRENT_WORLD_STATE (authoritative")
                    < prompt.indexOf("RECENT CONVERSATION");
            assert prompt.indexOf("RECENT CONVERSATION")
                    < prompt.indexOf("RECENT INVALIDATED/FAILED INTENTS");
            assert prompt.contains("AUTHORITATIVE FACT: No supported Hytale item/content");
            assert prompt.contains("desiredThing=drink, status=UNAVAILABLE");
            assert logs.stream().anyMatch(value -> value.contains("playerMessage=Drinks don't exist")
                    && value.contains("requested/desire=drink")
                    && value.contains("contentValidation=NOT_FOUND")
                    && value.contains("invalidatedIntent=drink")
                    && value.contains("contextConstraint=AUTHORITATIVE FACT"));

            ConversationSession otherPlayer = new ConversationSession(
                    UUID.randomUUID(), npcId, UUID.randomUUID(), Instant.now());
            assert otherPlayer.invalidatedIntents().isEmpty();
            assert session.isInvalidated("drink");
            service.shutdown();
        } finally {
            delete(directory);
        }
    }

    private static void verifyUnknownClaimAndHeldItemValidation() {
        UUID npcId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();
        ConversationSession session = new ConversationSession(
                UUID.randomUUID(), npcId, playerId, Instant.now());
        NpcPerceptionSnapshot held = heldSnapshot(npcId);
        ContentCatalog catalog = (thing, perception) -> {
            if (perception.focusedPlayerHeldItem() != null
                    && perception.focusedPlayerHeldItem().itemId().equals(thing)) {
                return new ContentValidationResult(thing, ContentValidationStatus.FOUND,
                        List.of(thing), "held ItemStack is authoritative");
            }
            return ContentValidationResult.unknown(thing, "registry unavailable in test");
        };
        ConversationGroundingService grounding = new ConversationGroundingService(catalog);
        session.appendTurn("What do you want?", "I'd like a moonberry.", Instant.now());
        var unknown = grounding.analyze(session,
                "Moonberries don't exist in Hytale.", held);
        assert unknown.contentValidation() == ContentValidationStatus.UNKNOWN;
        assert unknown.contextConstraint().startsWith("PLAYER CLAIM (not authoritative)");
        assert session.invalidatedIntents().isEmpty();

        var offered = grounding.analyze(session, "Do you want this?", held);
        assert offered.contentValidation() == ContentValidationStatus.FOUND;
        assert offered.requestedOrDesiredThing().equals("Item_Apple");
        assert offered.availableRelevantItems().stream()
                .anyMatch(item -> item.contains("Apple [Item_Apple, quantity=2]"));
    }

    private static LlmProvider provider(List<LlmRequest> requests, String response) {
        return new LlmProvider() {
            @Override
            public CompletableFuture<LlmResult> generateResponse(LlmRequest request) {
                requests.add(request);
                return CompletableFuture.completedFuture(new LlmResult(response,
                        new LlmLatency(Instant.now(), 0, 0, false)));
            }

            @Override
            public CompletableFuture<LlmProviderStatus> checkStatus() {
                return CompletableFuture.completedFuture(new LlmProviderStatus(
                        "test", "test", true, true, false, "test"));
            }

            @Override
            public String description() {
                return "test";
            }
        };
    }

    private static NpcProfile profile(UUID npcId) {
        return new NpcProfile(npcId, "Mara", "Villager with blacksmith training",
                "Direct and attentive", "Village resident", "Listen", "", "",
                List.of(), List.of(), List.of("BLACKSMITH"), List.of(), 5).validated();
    }

    private static NpcPerceptionSnapshot heldSnapshot(UUID npcId) {
        return new NpcPerceptionSnapshot(npcId, UUID.randomUUID(), UUID.randomUUID(),
                LocalDateTime.now(), 0, 64, 0, List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), 2,
                new PerceivedItem(null, "Item_Apple", "Apple", 2,
                        0, 0, "{}", 0), List.of());
    }

    private static String singular(String value) {
        String normalized = value == null ? "" : value.toLowerCase(java.util.Locale.ROOT)
                .strip();
        return normalized.endsWith("s") ? normalized.substring(0, normalized.length() - 1)
                : normalized;
    }

    private static void delete(Path directory) throws Exception {
        MemoryStore.flushAll();
        if (!Files.exists(directory)) {
            return;
        }
        try (var paths = Files.walk(directory)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (Exception failure) {
                    throw new RuntimeException(failure);
                }
            });
        }
    }
}
