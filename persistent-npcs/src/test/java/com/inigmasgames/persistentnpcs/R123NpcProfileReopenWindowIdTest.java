package com.inigmasgames.persistentnpcs;

import com.inigmasgames.persistentnpcs.ui.NpcProfilePage;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

/** Regression gate for monotonically increasing WindowManager IDs across Profile reopens. */
public final class R123NpcProfileReopenWindowIdTest {
    private static final Path GENERATED_DOCUMENTS = Path.of(
            "build/classes/Common/UI/Custom/Pages/ProfileInventory");

    private R123NpcProfileReopenWindowIdTest() { }

    public static void main(String[] arguments) throws Exception {
        Method resolver = NpcProfilePage.class.getDeclaredMethod(
                "boundNpcGridDocument", int.class);
        resolver.setAccessible(true);

        for (int id : new int[] {1, 8, 10, 13, 16, 19, 22, 1024}) {
            String expected = "Pages/ProfileInventory/NpcSection" + id + ".ui";
            assert expected.equals(resolver.invoke(null, id))
                    : "Profile must accept allocator-issued window ID " + id;
            Path document = GENERATED_DOCUMENTS.resolve("NpcSection" + id + ".ui");
            assert Files.isRegularFile(document)
                    : "Missing construction-time section document for ID " + id;
            assert Files.readString(document).contains("InventorySectionId: " + id + ";")
                    : "Section document must literally bind ID " + id;
        }

        assertRejected(resolver, 0);
        assertRejected(resolver, -1);
        assertRejected(resolver, 1025);

        String build = Files.readString(Path.of("build.ps1"));
        assert build.contains("for ($sectionId = 9; $sectionId -le 1024; $sectionId++)")
                : "Build must deterministically package post-probe window IDs";
        assert !Files.exists(GENERATED_DOCUMENTS.resolve("NpcSection1025.ui"))
                : "Generated section bundle must retain its explicit safety bound";

        System.out.println("R123 NPC Profile reopen window-ID gate passed.");
    }

    private static void assertRejected(Method resolver, int id) throws Exception {
        try {
            resolver.invoke(null, id);
            throw new AssertionError("Invalid section ID was accepted: " + id);
        } catch (InvocationTargetException expected) {
            assert expected.getCause() instanceof IllegalStateException;
        }
    }
}
