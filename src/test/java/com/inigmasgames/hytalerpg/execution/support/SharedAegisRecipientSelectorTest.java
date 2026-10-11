package com.inigmasgames.hytalerpg.execution.support;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SharedAegisRecipientSelectorTest {
    @Test void nearestClearCandidateWithinEightMetersWinsWithStableUuidTieBreak(){
        UUID first=UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID second=UUID.fromString("00000000-0000-0000-0000-000000000002");
        var candidates=List.of(
                new SharedAegisRecipientSelector.Candidate<>("through-wall",UUID.randomUUID(),1,false),
                new SharedAegisRecipientSelector.Candidate<>("beyond-range",UUID.randomUUID(),64.001,true),
                new SharedAegisRecipientSelector.Candidate<>("second",second,25,true),
                new SharedAegisRecipientSelector.Candidate<>("first",first,25,true));
        assertEquals(List.of("first","second"),SharedAegisRecipientSelector.ordered(candidates));
    }
    @Test void noVisibleRecipientDoesNotCreateAFallback(){
        assertTrue(SharedAegisRecipientSelector.ordered(List.of(
                new SharedAegisRecipientSelector.Candidate<>("wall",UUID.randomUUID(),4,false),
                new SharedAegisRecipientSelector.Candidate<>("far",UUID.randomUUID(),81,true))).isEmpty());
    }
}
