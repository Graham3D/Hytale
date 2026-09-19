package com.inigmasgames.persistentnpcs.conversation;

import com.inigmasgames.persistentnpcs.perception.NpcPerceptionSnapshot;
import com.inigmasgames.persistentnpcs.perception.PerceivedItem;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Session-scoped validation for concrete desires and player world corrections. */
public final class ConversationGroundingService {
    private static final Pattern NEGATED_EXISTENCE = Pattern.compile(
            "(?i)\\b([a-z][a-z -]{1,40}?)\\s+(?:do not|don't|does not|doesn't)\\s+exist\\b");
    private static final Pattern NO_CONTENT = Pattern.compile(
            "(?i)\\bthere\\s+(?:is|are)\\s+no\\s+([a-z][a-z -]{1,40}?)(?:[.!?,]|$)");
    private static final Pattern DESIRE = Pattern.compile(
            "(?i)\\b(?:i(?:'d| would)?\\s+(?:like|want|need)|i could (?:use|offer)|"
                    + "i(?:'ll| will) (?:have|take)|bring me|get me)"
                    + "\\s+(?:some\\s+|a\\s+|an\\s+|the\\s+)?([a-z][a-z-]{2,30})\\b");
    private static final Pattern SUGGESTION = Pattern.compile(
            "(?i)\\b(?:maybe|perhaps)\\s+(?:some\\s+|a\\s+|an\\s+|the\\s+)?"
                    + "([a-z][a-z-]{2,30})\\b");
    private static final Pattern HOPED_FOR = Pattern.compile(
            "(?i)\\b(?:i\\s+(?:was|am)\\s+hoping\\s+for|i\\s+wish\\s+for)\\s+"
                    + "(?:something\\s+to\\s+|some\\s+|a\\s+|an\\s+|the\\s+)?"
                    + "([a-z][a-z-]{2,30})\\b");
    private final ContentCatalog catalog;

    public ConversationGroundingService(ContentCatalog catalog) {
        this.catalog = catalog == null ? ContentCatalog.unavailable() : catalog;
    }

    public ConversationGrounding analyze(
            ConversationSession session,
            String playerMessage,
            NpcPerceptionSnapshot perception) {
        String claimThing = extractUnavailableClaim(playerMessage);
        String priorDesire = recentNpcDesire(session);
        String offeredItem = offeredHeldItem(playerMessage, perception);
        String requested = !claimThing.isBlank() ? claimThing
                : !offeredItem.isBlank() ? offeredItem : priorDesire;
        ContentValidationResult validation = requested.isBlank()
                ? ContentValidationResult.unknown("", "no concrete request detected")
                : catalog.validate(requested, perception);

        String invalidated = "";
        String constraint = "No new content constraint for this turn.";
        String playerClaim = claimThing.isBlank() ? "" : exact(playerMessage);
        if (!claimThing.isBlank()) {
            if (validation.status() == ContentValidationStatus.NOT_FOUND) {
                invalidated = claimThing;
                constraint = "AUTHORITATIVE FACT: No supported Hytale item/content matching \""
                        + claimThing + "\" was found. This invalidates the NPC desire; "
                        + "acknowledge it and choose an available alternative or no alternative.";
                session.invalidateIntent(claimThing, validation.reason(), Instant.now());
            } else if (validation.status() == ContentValidationStatus.FOUND) {
                session.validateIntent(claimThing);
                constraint = "AUTHORITATIVE FACT: Supported Hytale content matching \""
                        + claimThing + "\" exists. The player's contrary statement is only a claim.";
            } else {
                constraint = "PLAYER CLAIM (not authoritative): \"" + exact(playerMessage)
                        + "\" Content validation could not confirm or reject it.";
            }
        } else if (!offeredItem.isBlank()
                && validation.status() == ContentValidationStatus.FOUND) {
            session.validateIntent(offeredItem);
            constraint = "AUTHORITATIVE FACT: The offered held item exists in current perception: "
                    + offeredItem + ".";
        }

        revalidatePriorFailures(session, perception);
        return new ConversationGrounding(requested, validation.status(), invalidated,
                constraint, playerClaim, relevantItems(perception, validation.relevantItems()));
    }

    public static String extractDesire(String dialogue) {
        Matcher matcher = DESIRE.matcher(dialogue == null ? "" : dialogue);
        if (matcher.find()) {
            return concreteThing(matcher.group(1));
        }
        Matcher suggestion = SUGGESTION.matcher(dialogue == null ? "" : dialogue);
        if (suggestion.find()) {
            return concreteThing(suggestion.group(1));
        }
        Matcher hoped = HOPED_FOR.matcher(dialogue == null ? "" : dialogue);
        return hoped.find() ? concreteThing(hoped.group(1)) : "";
    }

    /** Deterministic final guard when a small model ignores a validated session constraint. */
    public String enforceModelDialogue(
            ConversationSession session,
            String dialogue,
            NpcPerceptionSnapshot perception) {
        String repeatedConstraint = repeatedInvalidatedRequest(session, dialogue);
        if (!repeatedConstraint.isBlank()) {
            return unavailableAcknowledgement(repeatedConstraint);
        }
        String desired = extractDesire(dialogue);
        if (desired.isBlank()) {
            return dialogue;
        }
        String invalidated = invalidatedFamily(session, desired);
        if (!invalidated.isBlank()) {
            return unavailableAcknowledgement(invalidated);
        }
        ContentValidationResult validation = catalog.validate(desired, perception);
        if (validation.status() != ContentValidationStatus.FOUND) {
            session.invalidateIntent(desired, validation.reason(), Instant.now());
            return unavailableAcknowledgement(desired);
        }
        return dialogue;
    }

    public boolean containsInvalidatedRequest(
            ConversationSession session, String dialogue) {
        return !repeatedInvalidatedRequest(session, dialogue).isBlank();
    }

    private void revalidatePriorFailures(
            ConversationSession session, NpcPerceptionSnapshot perception) {
        for (ConversationSession.InvalidatedIntent intent : session.invalidatedIntents()) {
            ContentValidationResult current = catalog.validate(intent.value(), perception);
            if (current.status() == ContentValidationStatus.FOUND) {
                session.validateIntent(intent.value());
            }
        }
    }

    private static String recentNpcDesire(ConversationSession session) {
        List<ConversationSession.ConversationTurn> turns = session.recentTurns(3);
        for (int index = turns.size() - 1; index >= 0; index--) {
            String desire = extractDesire(turns.get(index).npcReply());
            if (!desire.isBlank()) {
                return desire;
            }
        }
        return "";
    }

    private static String extractUnavailableClaim(String playerMessage) {
        String message = playerMessage == null ? "" : playerMessage;
        Matcher negated = NEGATED_EXISTENCE.matcher(message);
        if (negated.find()) {
            return normalizeThing(negated.group(1));
        }
        Matcher noContent = NO_CONTENT.matcher(message);
        return noContent.find() ? normalizeThing(noContent.group(1)) : "";
    }

    private static String offeredHeldItem(
            String playerMessage, NpcPerceptionSnapshot perception) {
        String message = playerMessage == null ? ""
                : playerMessage.toLowerCase(Locale.ROOT);
        boolean offering = message.contains("do you want this")
                || message.contains("want this") || message.contains("take this")
                || message.contains("i offer") || message.contains("offering")
                || message.contains("want it") || message.contains("have this");
        PerceivedItem held = perception == null ? null : perception.focusedPlayerHeldItem();
        return offering && held != null ? held.itemId() : "";
    }

    private static List<String> relevantItems(
            NpcPerceptionSnapshot perception, List<String> catalogMatches) {
        ArrayList<String> items = new ArrayList<>();
        if (perception != null && perception.focusedPlayerHeldItem() != null) {
            items.add(label(perception.focusedPlayerHeldItem()));
        }
        if (perception != null) {
            perception.nearbyItems().stream().limit(3).map(ConversationGroundingService::label)
                    .forEach(items::add);
            perception.npcInventory().stream().limit(3).map(ConversationGroundingService::label)
                    .forEach(items::add);
        }
        catalogMatches.stream().limit(3).forEach(items::add);
        return items.stream().distinct().limit(6).toList();
    }

    private static String label(PerceivedItem item) {
        String display = item.displayName() == null || item.displayName().isBlank()
                ? item.itemId() : item.displayName();
        return display + " [" + item.itemId() + ", quantity=" + item.quantity() + "]";
    }

    private static String normalizeThing(String value) {
        String normalized = value == null ? "" : value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9 _-]", " ").replaceAll("\\s+", " ").strip();
        if (normalized.endsWith("s") && normalized.length() > 3) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private static String concreteThing(String value) {
        String normalized = normalizeThing(value);
        return java.util.Set.of("", "to", "your", "this", "that", "nothing", "none",
                "help", "company", "silence", "rest").contains(normalized)
                ? "" : normalized;
    }

    private static String invalidatedFamily(ConversationSession session, String desired) {
        if (session.isInvalidated(desired)) {
            return desired;
        }
        for (ConversationSession.InvalidatedIntent intent : session.invalidatedIntents()) {
            if (intent.value().equals("drink") && isDrinkTerm(desired)) {
                return intent.value();
            }
        }
        return "";
    }

    private static String repeatedInvalidatedRequest(
            ConversationSession session, String dialogue) {
        String text = normalizeSentence(dialogue);
        boolean positiveRequest = java.util.stream.Stream.of(
                        " like ", " want ", " take ", " have ", " need ",
                        " hoping ", " wish ", " ask for ", " prefer ",
                        " drinking ", " drink ", " choose ", " use ", " offer ")
                .anyMatch(text::contains);
        if (!positiveRequest) {
            return "";
        }
        boolean negated = java.util.stream.Stream.of(
                        " do not ", " don t ", " would not ", " wouldn t ",
                        " can not ", " cannot ", " can t ", " no need ",
                        " not asking ", " don t want ", " don t need ")
                .anyMatch(text::contains);
        if (negated && !text.contains(" but ")) {
            return "";
        }
        for (ConversationSession.InvalidatedIntent intent : session.invalidatedIntents()) {
            if (intent.value().equals("drink")) {
                if (java.util.Set.of("drink", "water", "beverage", "juice", "ale", "beer",
                                "wine", "cider", "glass", "cup", "mug").stream()
                        .anyMatch(term -> text.contains(" " + term + " "))) {
                    return intent.value();
                }
            } else if (text.contains(" " + intent.value() + " ")) {
                return intent.value();
            }
        }
        return "";
    }

    private static String normalizeSentence(String value) {
        return " " + (value == null ? "" : value.toLowerCase(Locale.ROOT)
                .replace('\u2019', '\'').replaceAll("[^a-z0-9]+", " ")
                .replaceAll("\\s+", " ").strip()) + " ";
    }

    private static boolean isDrinkTerm(String value) {
        return java.util.Set.of("drink", "water", "beverage", "juice", "ale", "beer",
                "wine", "cider", "glass", "cup", "mug").contains(normalizeThing(value));
    }

    private static String unavailableAcknowledgement(String value) {
        // Do not echo the invalidated noun: repeating it can reassert the very request this
        // deterministic boundary is rejecting and can also leak unsafe model wording.
        return "All right. We'll leave that out.";
    }

    private static String exact(String value) {
        return value == null ? "" : value;
    }
}
