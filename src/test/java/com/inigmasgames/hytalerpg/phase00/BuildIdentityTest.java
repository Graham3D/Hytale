package com.inigmasgames.hytalerpg.phase00;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BuildIdentityTest {
    @Test void badgeShowsOnlyTheNumericRevision() {
        assertEquals("R262", BuildIdentity.displayRevision("R262C1ELITE4-CANDIDATE"));
        assertEquals("R250", BuildIdentity.displayRevision("R250-U7P5B"));
        assertEquals("R262", BuildIdentity.DISPLAY_REVISION);
        assertEquals("R262ME12S3B-072-QA3-CANDIDATE", BuildIdentity.REVISION);
        assertEquals("Hywind",BuildIdentity.QA_AUTO_SAVE);
        assertEquals(BuildIdentity.REVISION,BuildIdentity.QA_AUTO_REVISION);
        assertTrue(BuildIdentity.QA_AUTO_WORLDS.contains("3d366b79-f367-419a-87b7-a8d53cde7ec0"));
        assertTrue(BuildIdentity.QA_RECEIPT_PATH.endsWith("/build/guardian/verification.json"));
    }

    @Test void malformedRevisionCannotLeakToTheBadge() {
        assertThrows(IllegalArgumentException.class, () -> BuildIdentity.displayRevision("ELITE4"));
        assertThrows(IllegalArgumentException.class, () -> BuildIdentity.displayRevision("R"));
    }
}
