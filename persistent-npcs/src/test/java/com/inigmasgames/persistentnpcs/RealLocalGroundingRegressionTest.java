package com.inigmasgames.persistentnpcs;

import com.inigmasgames.persistentnpcs.config.ConfigRepository;
import com.inigmasgames.persistentnpcs.conversation.ContentCatalog;
import com.inigmasgames.persistentnpcs.conversation.ContentValidationResult;
import com.inigmasgames.persistentnpcs.conversation.ContentValidationStatus;
import com.inigmasgames.persistentnpcs.conversation.ConversationContextBuilder;
import com.inigmasgames.persistentnpcs.conversation.ConversationGroundingService;
import com.inigmasgames.persistentnpcs.conversation.ConversationSession;
import com.inigmasgames.persistentnpcs.conversation.MinimalWorldContext;
import com.inigmasgames.persistentnpcs.llm.OpenAiCompatibleProvider;
import com.inigmasgames.persistentnpcs.memory.MemoryStore;
import com.inigmasgames.persistentnpcs.perception.NpcPerceptionSnapshot;
import com.inigmasgames.persistentnpcs.perception.PerceivedItem;
import com.inigmasgames.persistentnpcs.profile.ProfileRepository;
import com.inigmasgames.persistentnpcs.relationship.RelationshipStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Real Nemotron coverage for the R008 grounded correction prompt. */
public final class RealLocalGroundingRegressionTest {
    private RealLocalGroundingRegressionTest() { }

    public static void main(String[] args) throws Exception {
        Path production = Path.of(System.getenv("APPDATA"), "Hytale", "UserData", "Saves",
                "NPC", "mods", "ImmersiveNPCs");
        var config = new ConfigRepository(production).load();
        OpenAiCompatibleProvider provider = new OpenAiCompatibleProvider(config);
        var status = provider.checkStatus().join();
        if (!status.reachable() || !status.reason().contains("configured model is available")) {
            System.out.println("Real R008 grounding regression skipped: " + status.reason());
            return;
        }
        Path temporary = Files.createTempDirectory("persistent-npc-real-r008-");
        try {
            var profile = new ProfileRepository(production).loadTestProfile();
            RelationshipStore relationships = new RelationshipStore(temporary);
            relationships.load();
            MemoryStore memories = new MemoryStore(temporary, 100);
            memories.load();
            ConversationContextBuilder builder = new ConversationContextBuilder(
                    relationships, memories, 6);
            ContentCatalog catalog = (thing, perception) -> {
                String normalized = singular(thing);
                if (normalized.equals("drink")) {
                    return new ContentValidationResult(thing,
                            ContentValidationStatus.NOT_FOUND, List.of(),
                            "no drink category exists in the validated test content snapshot");
                }
                if (perception.focusedPlayerHeldItem() != null
                        && perception.focusedPlayerHeldItem().itemId().equals(thing)) {
                    return new ContentValidationResult(thing, ContentValidationStatus.FOUND,
                            List.of(thing), "held ItemStack exists");
                }
                return ContentValidationResult.unknown(thing, "no concrete request");
            };
            ConversationGroundingService grounding = new ConversationGroundingService(catalog);
            ConversationSession session = new ConversationSession(UUID.randomUUID(),
                    profile.id(), UUID.randomUUID(), Instant.now());
            session.appendTurn("What would you like?", "I'd like a drink.", Instant.now());
            NpcPerceptionSnapshot empty = snapshot(profile.id(), null);
            String[] turns = {
                    "Drinks don't exist in Hytale. What would you like instead?",
                    "Would you still ask for one?", "What else could work?",
                    "Choose something grounded.", "Do you have another preference?",
                    "Final answer: what would you like?"
            };
            for (String player : turns) {
                var grounded = grounding.analyze(session, player, empty);
                var request = builder.build(session, profile, player,
                        new MinimalWorldContext("NPC", 0, 64, 0), empty, List.of(), grounded);
                String rawDialogue = provider.generateResponse(request).join().text().strip();
                String dialogue = grounding.enforceModelDialogue(
                        session, rawDialogue, empty);
                System.out.println("R008_GROUNDING player=" + player + " raw=" + rawDialogue
                        + " final=" + dialogue);
                if (ConversationGroundingService.extractDesire(dialogue).equals("drink")) {
                    throw new AssertionError("Nemotron repeated invalidated drink desire: "
                            + dialogue);
                }
                if (grounding.containsInvalidatedRequest(session, dialogue)) {
                    throw new AssertionError("Final dialogue repeated invalidated request: "
                            + dialogue);
                }
                String normalizedDialogue = dialogue.toLowerCase(Locale.ROOT);
                if (normalizedDialogue.contains("water")
                        || normalizedDialogue.contains("beverage")) {
                    throw new AssertionError("Nemotron invented an unavailable drink substitute: "
                            + dialogue);
                }
                session.appendTurn(player, dialogue, Instant.now());
            }

            NpcPerceptionSnapshot apple = snapshot(profile.id(),
                    new PerceivedItem(null, "Item_Apple", "Apple", 1,
                            0, 0, "{}", 0));
            ConversationSession offerSession = new ConversationSession(UUID.randomUUID(),
                    profile.id(), UUID.randomUUID(), Instant.now());
            String offer = "What exact item am I offering you, and do you want it?";
            var offeredGrounding = grounding.analyze(offerSession, offer, apple);
            var offerRequest = builder.build(offerSession, profile, offer,
                    new MinimalWorldContext("NPC", 0, 64, 0), apple, List.of(), offeredGrounding);
            String offeredReply = provider.generateResponse(offerRequest).join().text().strip();
            System.out.println("R008_HELD_ITEM response=" + offeredReply);
            if (!offeredReply.toLowerCase(Locale.ROOT).contains("apple")) {
                throw new AssertionError("Nemotron ignored authoritative held apple: "
                        + offeredReply);
            }
            System.out.println("Real R008 grounding regression passed.");
        } finally {
            delete(temporary);
        }
    }

    private static NpcPerceptionSnapshot snapshot(UUID npcId, PerceivedItem held) {
        return new NpcPerceptionSnapshot(npcId, UUID.randomUUID(), UUID.randomUUID(),
                LocalDateTime.now(), 0, 64, 0, List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), 1, held, List.of());
    }

    private static String singular(String value) {
        String normalized = value == null ? "" : value.toLowerCase(Locale.ROOT).strip();
        return normalized.endsWith("s") ? normalized.substring(0, normalized.length() - 1)
                : normalized;
    }

    private static void delete(Path directory) throws Exception {
        MemoryStore.flushAll();
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
