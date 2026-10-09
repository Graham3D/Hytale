package com.inigmasgames.hytalerpg.ui.inventory;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

final class NativeTooltipTextTest {
    @Test void heavyHideResolvesBothNativeItemReferences() {
        var names = Map.of("Ingredient_Leather_Heavy", "Heavy Leather", "Bench_Tannery", "Tanning Rack");
        String text = NativeTooltipText.plain(
                "Can be processed into <item is=\"Ingredient_Leather_Heavy\"/> at a <item is=\"Bench_Tannery\"/>.",
                names::get, key -> null);
        assertEquals("Can be processed into Heavy Leather at a Tanning Rack.", text);
    }

    @Test void preservesLocalizedNamesAndRegexSpecialCharacters() {
        assertEquals("Cuir épais / $5\\tool", NativeTooltipText.plain(
                "<item is='leather'/> / <item is=\"tool\" />",
                id -> id.equals("leather") ? "Cuir épais" : "$5\\tool", key -> null));
    }

    @Test void stripsNativeFormattingButRetainsTextAndParagraphs() {
        assertEquals("Contains Water.\n\nAncient wisdom.", NativeTooltipText.plain(
                "Contains <color is=\"#ffffff\">Water</color>.\\n\\n<i><b>Ancient</b> <s>wisdom</s>.</i>",
                id -> null, key -> null));
    }

    @Test void resolvesPortalDescriptionMessageAndItsNestedItemReference() {
        assertEquals("Use a Key.\nFlavor.", NativeTooltipText.plain(
                "<msg key=\"server.items.PortalKey.description\"/>\\n<i>Flavor.</i>",
                id -> "Key", key -> "Use a <item is=\"PortalKey\"/>."));
    }

    @Test void missingOrUnavailableAssetLeavesReadableTextWithoutMarkup() {
        assertEquals("Use Item.", NativeTooltipText.plain("Use <item is=\"missing\"/>.",
                id -> { throw new IllegalStateException("assets unavailable"); }, key -> null));
        assertEquals("Description", NativeTooltipText.plain("<msg key=\"missing\"/>Description",
                id -> null, key -> null));
    }

    @Test void recursiveTranslationIsBounded() {
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        assertEquals("", NativeTooltipText.plain("<msg key=\"loop\"/>", id -> null,
                key -> { calls.incrementAndGet(); return "<msg key=\"loop\"/>"; }));
        assertEquals(8, calls.get());
    }

    @Test void plainDescriptionsAndMathematicalComparisonsRemainUntouched() {
        assertEquals("Damage < 10 & level > 2", NativeTooltipText.plain(
                "Damage < 10 & level > 2", id -> null, key -> null));
        assertEquals("", NativeTooltipText.plain(null, id -> null, key -> null));
    }
}
