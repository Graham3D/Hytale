package com.inigmasgames.persistentnpcs;

import com.hypixel.hytale.protocol.packets.interface_.CustomUICommand;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import java.nio.file.Files;
import java.nio.file.Path;

/** Deterministic contract coverage for the Profile Editor document-tab navigation. */
public final class R169ProfileDocumentTabsTest {
    private static final Path PROFILE_UI = Path.of(
            "src/main/resources/Common/UI/Custom/Pages/ImmersiveNpcProfile.ui");
    private static final Path ALL_SECTIONS_UI = Path.of(
            "src/main/resources/Common/UI/Custom/Pages/ProfileEditor/AllSections.ui");
    private static final Path PAGE_SOURCE = Path.of(
            "src/main/java/com/inigmasgames/persistentnpcs/ui/NpcProfilePage.java");

    private R169ProfileDocumentTabsTest() { }

    public static void main(String[] arguments) throws Exception {
        selectedRailRowsReuseRecorderMarker();
        navigationUsesNativeScrollWithoutRebuildingTheDraft();
        sectionGeometryPreventsTitleOverlap();
        System.out.println("R169 PASS: Profile rail uses the Recorder marker, native child scrolling, and corrected section geometry.");
    }

    private static void selectedRailRowsReuseRecorderMarker() throws Exception {
        String ui = Files.readString(PROFILE_UI);
        for (String section : new String[] {
                "BasicInfo", "Background", "Personality", "ValuesBeliefs",
                "Motivations", "Relationships", "SpeechStyle", "Notes"
        }) {
            assert ui.contains("Button #ProfileCategory" + section)
                    : "Missing section button " + section;
            assert ui.contains("#ProfileCategory" + section + "Selected")
                    : "Missing selected marker " + section;
        }
        assert occurrences(ui, "ImmersiveNpcInventory/NpcIconSelectSample.png") == 9
                : "The Recorder marker plus every section must use the same packaged asset";
        assert !ui.contains("Text: \"? ") : "Missing-glyph selection text must not return";

        String source = Files.readString(PAGE_SOURCE);
        assert !source.contains("▶") : "Unsupported Unicode triangle must not be emitted";
        assert !source.contains("ProfileCategory\" + category.resourceName + \".Text")
                : "Selection state must not rewrite button labels";
    }

    private static void navigationUsesNativeScrollWithoutRebuildingTheDraft() throws Exception {
        String source = Files.readString(PAGE_SOURCE);
        assert source.contains("navigateProfileEditorSection();")
                : "Section events must use the document-tab route";
        assert source.contains("#ProfileForm.ScrollChildIndexIntoView")
                : "Navigation must use Hytale's native scroll-to-child property";
        for (int index = 0; index < 8; index++) {
            assert source.contains("\", " + index + ")")
                    : "Missing stable section child index " + index;
        }

        UICommandBuilder commands = new UICommandBuilder();
        commands.set("#ProfileForm.ScrollChildIndexIntoView", 5);
        CustomUICommand[] emitted = commands.getCommands();
        assert emitted.length == 1 : "Scroll navigation must be one focused UI command";
        assert "#ProfileForm.ScrollChildIndexIntoView".equals(emitted[0].selector)
                : "Scroll command selector/property must remain intact";
        assert !source.substring(source.indexOf("private void navigateProfileEditorSection()"),
                        source.indexOf("private void refreshProfileEditorForm()"))
                .contains("clear(")
                : "Document-tab navigation must not rebuild the form or discard draft input";
    }

    private static void sectionGeometryPreventsTitleOverlap() throws Exception {
        String ui = Files.readString(ALL_SECTIONS_UI);
        assert ui.contains("Group #SectionPersonality { LayoutMode: Top; Anchor: (Height: 420")
                : "Personality must contain Temperament, Likes, and Dislikes";
        assert ui.contains("Group #SectionValues { LayoutMode: Top; Anchor: (Height: 350")
                : "Values must contain its heading, label, field, and helper text";
    }

    private static int occurrences(String text, String needle) {
        int count = 0;
        int offset = 0;
        while ((offset = text.indexOf(needle, offset)) >= 0) {
            count++;
            offset += needle.length();
        }
        return count;
    }
}
