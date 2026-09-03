package com.inigmasgames.persistentnpcs.conversation;

import com.inigmasgames.persistentnpcs.llm.LlmLatency;
import com.inigmasgames.persistentnpcs.conversation.contract.DecisionContract;
import com.inigmasgames.persistentnpcs.voice.SpeechPhraseChunker;
import com.inigmasgames.persistentnpcs.voice.VocalState;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Deterministic one-completion/one-response coverage for R060. */
public final class R060CanonicalResponseAssemblyTest {
    private R060CanonicalResponseAssemblyTest() { }

    public static void main(String[] args) {
        generatedLinesBecomeOneCanonicalResponse();
        everyCanonicalSentenceKeepsOneResponseIdAndOrder();
        finalProviderTailCannotBecomeTextOnly();
        attachedDashCannotCreateSyntheticChunkWhitespace();
        dialogueTextNeverUsesStructuredDecisionParsing();
        conversationOutcomeEnforcesCanonicalAssembly();
        System.out.println("R060 canonical response assembly tests passed.");
    }

    private static void generatedLinesBecomeOneCanonicalResponse() {
        String assembled = CanonicalDialogueAssembler.assemble(
                "First sentence.\r\nSecond sentence!\n\nThird sentence?");
        assert assembled.equals("First sentence. Second sentence! Third sentence?")
                : assembled;
    }

    private static void everyCanonicalSentenceKeepsOneResponseIdAndOrder() {
        UUID responseId = UUID.randomUUID();
        List<CommittedDialogueResponse.CommittedChunk> delivered = new ArrayList<>();
        CommittedDialogueResponse response = new CommittedDialogueResponse(
                responseId, delivered::add);
        SpeechPhraseChunker chunker = SpeechPhraseChunker.exact((index, phrase, state) ->
                response.commit(phrase, state));
        VocalState state = VocalState.infer("First sentence. Second sentence! Third sentence?");
        chunker.accept("First sentence.\nSecond sentence!\r\nThird sentence?", state);
        chunker.complete("First sentence.\nSecond sentence!\r\nThird sentence?", state);
        // The latency chunker may coalesce adjacent short sentences, but it must retain all
        // lexical content under the same response identity and contiguous ordering.
        assert delivered.size() >= 2 : delivered;
        for (int index = 0; index < delivered.size(); index++) {
            assert delivered.get(index).responseId().equals(responseId);
            assert delivered.get(index).chunkIndex() == index;
        }
        assert response.text().equals(
                "First sentence. Second sentence! Third sentence?") : response.text();
    }

    private static void finalProviderTailCannotBecomeTextOnly() {
        List<String> phrases = new ArrayList<>();
        SpeechPhraseChunker chunker = SpeechPhraseChunker.exact(
                (index, phrase, state) -> phrases.add(phrase));
        VocalState state = VocalState.infer("One sentence. A second sentence follows.");
        chunker.accept("One sentence. A sec", state);
        chunker.complete("One sentence.\nA second sentence follows.", state);
        assert phrases.equals(List.of("One sentence.", "A second sentence follows."))
                : phrases;
    }

    private static void attachedDashCannotCreateSyntheticChunkWhitespace() {
        String canonical = "Hello! I was just marveling at that strange-looking gear you "
                + "brought—did it hum or did it just stare at me?";
        List<String> phrases = new ArrayList<>();
        SpeechPhraseChunker chunker = SpeechPhraseChunker.exact(
                (index, phrase, state) -> phrases.add(phrase));
        chunker.complete(canonical, VocalState.infer(canonical));
        assert String.join(" ", phrases).equals(canonical) : phrases;
    }

    private static void dialogueTextNeverUsesStructuredDecisionParsing() {
        assert ConversationService.wordingOnlyContract(true, false,
                DecisionContract.dialogue(true));
        assert ConversationService.wordingOnlyContract(true, false,
                DecisionContract.dialogue(false));
        assert !ConversationService.wordingOnlyContract(false, false,
                DecisionContract.dialogue(true));
        assert !ConversationService.wordingOnlyContract(true, true,
                DecisionContract.actionResult());
        assert !ConversationService.wordingOnlyContract(true, false,
                DecisionContract.choice());
    }

    private static void conversationOutcomeEnforcesCanonicalAssembly() {
        ConversationOutcome outcome = new ConversationOutcome(UUID.randomUUID(),
                "One line.\nAnother line.",
                new LlmLatency(Instant.now(), 1, 2, true), 3);
        assert outcome.dialogue().equals("One line. Another line.") : outcome.dialogue();
    }
}
