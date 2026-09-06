package com.inigmasgames.hytalerpg;

import com.inigmasgames.hytalerpg.content.CatalogResolution;
import com.inigmasgames.hytalerpg.content.RpgCatalog;
import com.inigmasgames.hytalerpg.domain.LinkNodeId;
import com.inigmasgames.hytalerpg.domain.PassiveSlot;
import com.inigmasgames.hytalerpg.domain.SkillSlot;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CatalogAndSlotTest {
    @Test void allPermanentSlotParsersAreExactAndBounded() {
        for (int index = 1; index <= 4; index++) {
            assertEquals(index - 1, SkillSlot.parse("skill0" + index).index());
            assertEquals(LinkNodeId.NodeKind.SKILL, LinkNodeId.parse("skill" + index).kind());
        }
        for (int index = 1; index <= 6; index++) {
            assertEquals(index - 1, PassiveSlot.parse("passive0" + index).index());
            assertEquals(LinkNodeId.NodeKind.PASSIVE, LinkNodeId.parse("passive" + index).kind());
        }
        assertEquals(LinkNodeId.JOINT01, LinkNodeId.parse("joint1"));
        assertEquals(LinkNodeId.JOINT02, LinkNodeId.parse("joint02"));
        assertThrows(IllegalArgumentException.class, () -> PassiveSlot.parse("passive07"));
        assertThrows(IllegalArgumentException.class, () -> SkillSlot.parse("skill05"));
    }

    @Test void canonicalCatalogLoadsAndResolvesFormattingWithoutGuessing() {
        RpgCatalog catalog = RpgCatalog.loadCanonical();
        assertEquals(87, catalog.skills().size());
        assertEquals(66, catalog.passives().size());
        assertEquals("fire_bolt", catalog.resolveSkill("firebolt").value().id().value());
        assertEquals("fire_bolt", catalog.resolveSkill("fire_bolt").value().id().value());
        assertEquals("fire_bolt", catalog.resolveSkill("Fire Bolt").value().id().value());
        assertEquals("expanded_radius", catalog.resolvePassive("Expanded Area").value().id().value());
        assertEquals(CatalogResolution.Status.AMBIGUOUS, catalog.resolveSkill("fire").status());
        assertEquals(CatalogResolution.Status.NOT_FOUND, catalog.resolvePassive("definitely_missing").status());
    }
}
