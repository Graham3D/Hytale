package com.inigmasgames.hytalerpg.ui.hud;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SkillFailureNoticeTest {
    @Test void fadesAroundOneSecondHoldAndRepeatedFailureDoesNotKeepRestartingIt() {
        var notice = new SkillFailureNotice();
        assertEquals(-1, notice.view(0).phase());
        notice.show("COOLDOWN_ACTIVE", 0);
        assertEquals(0, notice.view(0).phase());
        assertEquals(1, notice.view(90_000_000).phase());
        assertEquals(2, notice.view(180_000_000).phase());
        notice.show("COOLDOWN_ACTIVE", 1_000_000_000);
        assertEquals(2, notice.view(1_179_999_999).phase());
        assertEquals(1, notice.view(1_180_000_000).phase());
        assertEquals(0, notice.view(1_270_000_000).phase());
        assertEquals(new SkillFailureNotice.View("", -1), notice.view(1_360_000_000));
        notice.show("NO_AIMED_GROUND_ITEM", 1_400_000_000);
        assertEquals("Aim at dropped Hywind gear within 6 blocks", notice.view(1_400_000_000).text());
    }
    @Test void describesResourceWeaponAndTargetFailures() {
        assertEquals("Insufficient resources to cast", SkillFailureNotice.message("INSUFFICIENT_RESOURCE"));
        assertTrue(SkillFailureNotice.message("INVALID_MAIN_HAND").contains("main-hand weapon"));
        assertTrue(SkillFailureNotice.message("IRON_SENTINEL_WORLD_ITEM_GONE").contains("no longer available"));
        assertEquals("Cannot cast: UNKNOWN_GATE", SkillFailureNotice.message("UNKNOWN_GATE"));
    }
}
