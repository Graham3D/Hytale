package com.inigmasgames.persistentnpcs;

import com.google.gson.JsonParseException;
import com.inigmasgames.persistentnpcs.json.JsonFiles;
import com.inigmasgames.persistentnpcs.profile.NpcProfile;
import com.inigmasgames.persistentnpcs.profile.ProfileRepository;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/** Regression for the post-update Gson UUID adapter and identity migration. */
public final class R094HytaleUuidCompatibilityTest {
    private R094HytaleUuidCompatibilityTest() { }

    public static void main(String[] args) throws Exception {
        blankLegacyIdentityMigratesOnce();
        oneExistingIdentityIsPreserved();
        malformedNonEmptyIdentityStillFails();
        System.out.println("R094 Hytale UUID compatibility tests passed.");
    }

    private static void blankLegacyIdentityMigratesOnce() throws Exception {
        Path data = Files.createTempDirectory("immersive-r094-blank");
        Path profile = data.resolve("profiles/Jonalith/Jonalith.json");
        Files.createDirectories(profile.getParent());
        Files.writeString(profile, json("", "", "Jonalith"), StandardCharsets.UTF_8);
        ProfileRepository repository = new ProfileRepository(data);
        NpcProfile loaded = repository.loadAll().get("jonalith");
        UUID expected = UUID.nameUUIDFromBytes(
                "ImmersiveNPCs:profile:jonalith".getBytes(StandardCharsets.UTF_8));
        assert loaded.id().equals(expected);
        assert loaded.stableId().equals(expected);
        NpcProfile persisted = JsonFiles.read(profile, NpcProfile.class);
        assert persisted.id().equals(expected);
        assert persisted.stableId().equals(expected);
        assert Files.isRegularFile(profile.resolveSibling(
                "Jonalith.json.pre-r094-uuid-migration"));
        assert repository.loadAll().get("jonalith").stableId().equals(expected)
                : "Restart must not recreate a persistent identity";
    }

    private static void oneExistingIdentityIsPreserved() throws Exception {
        Path data = Files.createTempDirectory("immersive-r094-existing");
        Path profile = data.resolve("profiles/Rowan/Rowan.json");
        Files.createDirectories(profile.getParent());
        UUID identity = UUID.randomUUID();
        Files.writeString(profile, json(identity.toString(), "", "Rowan"),
                StandardCharsets.UTF_8);
        NpcProfile loaded = new ProfileRepository(data).load("Rowan");
        assert loaded.id().equals(identity);
        assert loaded.stableId().equals(identity);
        assert JsonFiles.read(profile, NpcProfile.class).stableId().equals(identity);
    }

    private static void malformedNonEmptyIdentityStillFails() throws Exception {
        Path data = Files.createTempDirectory("immersive-r094-invalid");
        Path profile = data.resolve("profiles/Bad/Bad.json");
        Files.createDirectories(profile.getParent());
        Files.writeString(profile, json("not-a-uuid", "", "Bad"), StandardCharsets.UTF_8);
        boolean rejected = false;
        try {
            new ProfileRepository(data).load("Bad");
        } catch (JsonParseException expected) {
            rejected = expected.getMessage().contains("Invalid non-empty UUID");
        }
        assert rejected;
    }

    private static String json(String id, String stableId, String name) {
        return """
                {
                  "id": "%s",
                  "stableId": "%s",
                  "name": "%s",
                  "role": "Hunter",
                  "personality": "Observant",
                  "biography": "Lives locally.",
                  "purpose": "Act truthfully.",
                  "defaultDisposition": 0
                }
                """.formatted(id, stableId, name);
    }
}
