package com.inigmasgames.hytalerpg.ui.skilltree;

import com.google.gson.Gson;
import com.inigmasgames.hytalerpg.content.RpgCatalog;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.*;

class RpgIconIndexTest {
    private RpgSkillIcons.Index index() throws Exception {
        return new Gson().fromJson(Files.readString(Path.of("src/main/resources/rpg/presentation/icon-index.json")), RpgSkillIcons.Index.class);
    }
    @Test void indexAndOwnerCsvCoverEveryCanonicalSkillAndPassiveWithoutFilenameCollisions() throws Exception {
        var entries = List.of(index().entries()); var catalog = RpgCatalog.loadCanonical();
        assertEquals(153, entries.size());
        assertEquals(catalog.skills().stream().map(s -> s.id().value()).collect(Collectors.toSet()),
                entries.stream().filter(e -> e.kind().equals("Skill")).map(RpgSkillIcons.Entry::id).collect(Collectors.toSet()));
        assertEquals(catalog.passives().stream().map(p -> p.id().value()).collect(Collectors.toSet()),
                entries.stream().filter(e -> e.kind().equals("Passive")).map(RpgSkillIcons.Entry::id).collect(Collectors.toSet()));
        assertEquals(153, entries.stream().map(e -> e.fileName().toLowerCase(Locale.ROOT)).distinct().count());
        var csv = Files.readString(Path.of("art/ICON-FILENAMES.csv"));
        for (var e : entries) {
            assertTrue(csv.contains("\"" + e.name() + "\",\"" + e.fileName() + "\""));
            if (e.kind().equals("Skill")) assertTrue(Files.isRegularFile(Path.of("src/main/resources").resolve(e.itemAsset())));
        }
    }
    @Test void allFutureIconsResolveByPresenceAndKeepFallbackWhenMissing() throws Exception {
        var present = RpgSkillIcons.resolve(index(), path -> true);
        var missing = RpgSkillIcons.resolve(index(), path -> false);
        for (var entry : index().entries()) {
            String key = entry.kind() + ":" + entry.id();
            assertEquals("Icons/RPG/" + entry.fileName(), present.get(key));
            assertEquals(RpgSkillTreeProjectionService.PLACEHOLDER_ICON, missing.get(key));
        }
        assertEquals("Icons/RPG/SkillFirebolt.png", present.get("Skill:fire_bolt"));
        assertEquals("Icons/RPG/SkillFireball.png", present.get("Skill:fireball"));
        assertEquals("Icons/RPG/SkillWhirlwind.png", present.get("Skill:whirlwind"));
        assertEquals("Icons/RPG/PassivePotency.png", present.get("Passive:potency"));
        assertEquals(RpgSkillTreeProjectionService.PLACEHOLDER_ICON, RpgSkillIcons.forPassive("unknown"));
    }
    @Test void unsafeIndexPathsAndDuplicateIdsAreRejected() {
        var unsafe = new RpgSkillIcons.Entry("Skill", "whirlwind", "Whirlwind", "../SkillWhirlwind.png", "");
        assertThrows(IllegalStateException.class, () -> RpgSkillIcons.resolve(new RpgSkillIcons.Index(1, new RpgSkillIcons.Entry[]{unsafe}), p -> true));
        var valid = new RpgSkillIcons.Entry("Passive", "potency", "Potency", "PassivePotency.png", "");
        assertThrows(IllegalStateException.class, () -> RpgSkillIcons.resolve(new RpgSkillIcons.Index(1, new RpgSkillIcons.Entry[]{valid, valid}), p -> true));
        assertThrows(IllegalStateException.class, () -> RpgSkillIcons.resolve(new RpgSkillIcons.Index(2, new RpgSkillIcons.Entry[]{valid}), p -> true));
    }
}
