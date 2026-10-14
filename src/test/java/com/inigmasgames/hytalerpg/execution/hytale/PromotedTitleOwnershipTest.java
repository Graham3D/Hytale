package com.inigmasgames.hytalerpg.execution.hytale;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PromotedTitleOwnershipTest {
    @Test void coloredTitleReplacesDifferentNativeOrAuthoredName() {
        var colored=new PromotedNameGlyphs.Name("Champion Grizzly Bear","Champion",
                List.of(new PromotedNameGlyphs.Glyph(0,0,null)));
        assertEquals("",EnemyHealthBarPresentation.nativePlateText(colored,"Grizzly Bear"));
        assertEquals("",EnemyHealthBarPresentation.nativePlateText(colored,"Authored Bear"));
    }

    @Test void nativeTitleRemainsWhenColoredTitleIsUnavailable() {
        assertEquals("Grizzly Bear",EnemyHealthBarPresentation.nativePlateText(null,"Grizzly Bear"));
        assertEquals("Grizzly Bear",EnemyHealthBarPresentation.nativePlateText(
                new PromotedNameGlyphs.Name("Champion Grizzly Bear","Champion",List.of()),"Grizzly Bear"));
    }
}
