package com.inigmasgames.hytalerpg.phase00;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BuildIdentityTest {
    @Test void badgeShowsOnlyTheNumericRevision() {
        assertEquals("R262", BuildIdentity.displayRevision("R262C1ELITE4-CANDIDATE"));
        assertEquals("R250", BuildIdentity.displayRevision("R250-U7P5B"));
        assertEquals("R262", BuildIdentity.DISPLAY_REVISION);
        assertEquals("R262ME12S3B-072-CANDIDATE", BuildIdentity.REVISION);
    }

    @Test void malformedRevisionCannotLeakToTheBadge() {
        assertThrows(IllegalArgumentException.class, () -> BuildIdentity.displayRevision("ELITE4"));
        assertThrows(IllegalArgumentException.class, () -> BuildIdentity.displayRevision("R"));
    }
}
