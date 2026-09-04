package com.inigmasgames.persistentnpcs;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

/** Static packaging/layout gate, not a substitute for connected client rendering. */
public final class R146NpcProfileMainMenuPolishTest {
    private static final Path PAGES = Path.of("src/main/resources/Common/UI/Custom/Pages");

    public static void main(String[] args) throws Exception {
        String ui = Files.readString(PAGES.resolve("ImmersiveNpcProfile.ui"));
        String overview = block(ui, "$C.@PageOverlay #ProfilePage");
        String page = Files.readString(Path.of(
                "src/main/java/com/inigmasgames/persistentnpcs/ui/NpcProfilePage.java"));
        assert overview.contains("Width: 1180, Height: 890");
        assert overview.contains("$C.@DecoratedContainer #TopWorkspace");
        assert overview.contains("$C.@DecoratedContainer #InventoriesPanel");
        assert overview.contains("$C.@Title #ProfileTitle");
        assert !overview.contains("LIVE INVENTORY");
        assert block(overview, "Group #ProfileAssetsPanel").contains("Visible: false;");
        assert block(overview, "Group #LegacyProfileActions").contains("Visible: false;");
        assert block(overview, "Group #WorkspaceFooter").contains("Close Profile");
        assert page.contains("commands.set(\"#AuthoringBackNavigation.Visible\", voiceEditor)");
        for (String id : new String[] {"OverviewButton", "InventoryButton",
                "AppearanceEditorButton", "ProfileEditorButton", "VoiceRecorderButton"}) {
            assert overview.indexOf("#" + id) == overview.lastIndexOf("#" + id);
            assert block(overview, "Group #ProfileNavigation").contains("Button #" + id);
            assert page.contains("\"#" + id + "\"") : "Missing event binding: " + id;
        }
        String navigation = page.substring(page.indexOf("if (\"NAV_OVERVIEW\""),
                page.indexOf("if (isInventoryDrop(data))"));
        assert page.indexOf("authoringSession.validate") < page.indexOf("if (\"NAV_OVERVIEW\"");
        assert navigation.contains("EditorKind.NONE");
        assert navigation.contains("sendUpdate(commands, false)");
        assert !navigation.contains("append(") && !navigation.contains("moveItemStack");
        assert block(overview, "Group #OverviewSelected").contains("Visible: true;");
        assert block(overview, "Group #InventorySelected").contains("Visible: false;");
        String arrows = block(overview, "Group #TransferRail");
        assert arrows.contains("HitTestVisible: false;");
        assert arrows.contains("DefaultDropdownCaret.png");
        assert arrows.contains("DefaultDropdownCaretLeft.png");
        assert !arrows.contains("Button") && !arrows.contains("ItemGrid");
        assert !page.contains("\"#TransferRail\"");
        assert overview.indexOf("#NpcAppearancePanel") < overview.indexOf("#ArmorSlotColumn");
        assert overview.indexOf("#ArmorSlotColumn") < overview.indexOf("#LoadoutSlotColumn");
        assert block(overview, "Group #NpcAppearancePanel").contains("#StatsStrip");
        for (String label : new String[] {"HEAD", "CHEST", "HANDS", "LEGS", "WEAPON", "SHIELD", "AMMO"}) {
            assert overview.contains("Text: \"" + label + "\"");
        }
        for (String asset : new String[] {"ProfileNavOverview@2x.png", "ProfileNavInventory@2x.png",
                "ProfileNavAppearance@2x.png", "ProfileNavEditor@2x.png", "ProfileNavVoice.png",
                "DefaultDropdownCaret@2x.png", "DefaultDropdownCaretLeft@2x.png"}) {
            byte[] bytes = Files.readAllBytes(PAGES.resolve("ImmersiveNpcInventory").resolve(asset));
            assert bytes.length > 8 && bytes[0] == (byte) 0x89 && bytes[1] == 'P'
                    && bytes[2] == 'N' && bytes[3] == 'G' : "Missing/invalid PNG: " + asset;
        }
        String compact = Files.readString(PAGES.resolve("ProfileInventory/GridCommon.ui"));
        String probe = Files.readString(PAGES.resolve("NativeInventoryProbe/GridCommon.ui"));
        assert compact.contains("SlotSize: 48,") && compact.contains("Width: 504, Height: 204");
        assert probe.contains("SlotSize: 62,") : "Do not resize isolated probe resources";
        assert behaviorOnly(compact).equals(behaviorOnly(probe))
                : "Only grid geometry may differ from the proven section-bound template";
        for (int id = 1; id <= 1024; id++) {
            String profile = Files.readString(Path.of("build/classes/Common/UI/Custom/Pages/"
                    + "ProfileInventory/NpcSection" + id + ".ui"));
            String nativeProbe = Files.readString(Path.of("build/classes/Common/UI/Custom/Pages/"
                    + "NativeInventoryProbe/NpcSection" + id + ".ui"));
            assert profile.contains("InventorySectionId: " + id + ";");
            assert profile.strip().equals(nativeProbe.strip());
        }
        // Document-space checks at both requested sizes. Hytale UI scaling and
        // actual hit testing still require the connected acceptance checklist.
        for (int[] viewport : new int[][] {{1920, 1080}, {2560, 1440}}) {
            assert (viewport[0] - 1180) / 2 >= 0;
            assert (viewport[1] - 890) / 2 >= 0;
        }
        assert 494 + 12 + 310 + 10 + 24 + 6 + 34 <= 890;
        assert 30 + 312 + 8 + 64 <= 494 - 38 - 34;
        assert 210 + 16 + 440 + 16 + 190 + 12 + 210 <= 1180 - 34;
        assert 504 <= (1180 - 34 - 62) / 2;
        assert 204 + 28 <= 310 - 38 - 34;
        assert 10 * 48 + 9 * 2 + 4 <= 504;
        assert 4 * 48 + 3 * 2 + 4 <= 204;
        // Parameters cannot follow ordinary properties in a Hytale template.
        assert !Pattern.compile("\\$C\\.@Title[^{}]*\\{[^{}]*Anchor:[^{}]*@Text\\s*=")
                .matcher(ui).find() : "R145 title parser regression";
        System.out.println("R146 Profile main-menu packaging/layout gate passed; connected rendering pending.");
    }

    private static String behaviorOnly(String grid) {
        return grid.replaceAll("(?m)^.*(?:Anchor:|SlotSize:|SlotIconSize:).*(?:\\R|$)", "").strip();
    }

    private static String block(String source, String selector) {
        int from = source.indexOf(selector);
        assert from >= 0 : "Missing " + selector;
        int begin = source.indexOf('{', from);
        int depth = 1;
        for (int i = begin + 1; i < source.length(); i++) {
            if (source.charAt(i) == '{') depth++;
            if (source.charAt(i) == '}' && --depth == 0) return source.substring(from, i + 1);
        }
        throw new AssertionError("Unbalanced block: " + selector);
    }
}
