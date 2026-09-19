package com.inigmasgames.persistentnpcs;

import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBinding;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.inigmasgames.persistentnpcs.appearance.NpcAppearanceCatalogService.Category;
import com.inigmasgames.persistentnpcs.authoring.NpcAuthoringSession;
import com.inigmasgames.persistentnpcs.authoring.NpcAuthoringSessionRegistry;
import com.inigmasgames.persistentnpcs.ui.NpcProfilePage;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import sun.misc.Unsafe;

/** Connected-log regression for literal Appearance selector payloads in R156. */
public final class R157AppearanceEventPayloadHotfixTest {
    private static final Unsafe UNSAFE = unsafe();

    private R157AppearanceEventPayloadHotfixTest() { }

    public static void main(String[] arguments) throws Exception {
        serverOwnedIdentitiesAreEmbeddedAsConcretePayloads();
        clientSearchUsesTheNativeOutputBindingContract();
        System.out.println("R157 PASS: Appearance payloads embed server-owned identities and use @ output binding only for client search text.");
    }

    private static void serverOwnedIdentitiesAreEmbeddedAsConcretePayloads() throws Exception {
        NpcAuthoringSession session = NpcAuthoringSessionRegistry.shared().acquire(
                UUID.randomUUID(), UUID.randomUUID(), null, Map.of("appearance", "test"),
                ignored -> true, ignored -> { });
        try {
            session.ready();
            session.openEditor(NpcAuthoringSession.EditorKind.APPEARANCE);
            NpcProfilePage page = (NpcProfilePage) UNSAFE.allocateInstance(NpcProfilePage.class);
            put(page, "authoringSession", session);

            UIEventBuilder events = new UIEventBuilder();
            invoke(page, "bindAppearanceCatalogEvents",
                    new Class<?>[] { UIEventBuilder.class, String.class, List.class,
                            List.class, String.class, String.class, List.class },
                    events, "page-hash-157",
                    List.of(Category.PANTS, Category.OVERPANTS),
                    List.of("Apprentice_Pants", "Bannerlord_Quilted"),
                    "Apprentice_Pants", "BROWN", List.of("DEFAULT", "ROLLED"));
            invoke(page, "bindAppearanceColors",
                    new Class<?>[] { UIEventBuilder.class, List.class, String.class,
                            String.class, String.class },
                    events, List.of("BROWN", "GOLD"), "page-hash-157",
                    "Apprentice_Pants", "DEFAULT");

            int checked = 0;
            boolean category = false;
            boolean option = false;
            boolean variant = false;
            boolean color = false;
            for (CustomUIEventBinding binding : events.getEvents()) {
                String data = binding.data;
                if (data == null || !data.contains("APPEARANCE_")) continue;
                checked++;
                assert !data.contains("#Appearance")
                        : "Server-owned payload must not contain a UI selector: " + data;
                assert !binding.locksInterface : "Catalog interactions remain non-locking";
                category |= data.contains("PANTS");
                option |= data.contains("page-hash-157") && data.contains("Apprentice_Pants");
                variant |= data.contains("ROLLED") && data.contains("BROWN");
                color |= data.contains("GOLD") && data.contains("DEFAULT");
            }
            assert checked == 10 : "Expected 2 categories + 2 options + 2 pages + 2 variants + 2 colors";
            assert category && option && variant && color
                    : "Every concrete Appearance payload family must be represented";
        } finally {
            session.close();
        }
    }

    private static void clientSearchUsesTheNativeOutputBindingContract() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/inigmasgames/persistentnpcs/ui/NpcProfilePage.java"));
        assert source.contains(".append(\"@AppearanceSearch\", \"#AppearanceSearchInput.Value\")");
        assert source.contains("new KeyedCodec<>(\"@AppearanceSearch\", Codec.STRING)");
        for (String unsupported : List.of(
                ".append(\"AppearanceCatalogHash\", \"#AppearanceCatalogHash.Text\")",
                ".append(\"AppearanceOptionId\", \"#AppearanceCurrentId.Text\")",
                ".append(\"AppearanceCategory\", \"#AppearanceCategory\"")) {
            assert !source.contains(unsupported)
                    : "Unsupported ordinary-key selector interpolation returned: " + unsupported;
        }
    }

    private static Object invoke(Object target, String name, Class<?>[] parameterTypes,
            Object... arguments) throws Exception {
        Method method = target.getClass().getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        return method.invoke(target, arguments);
    }

    private static void put(Object target, String name, Object value) {
        Field field = findField(target.getClass(), name);
        UNSAFE.putObject(target, UNSAFE.objectFieldOffset(field), value);
    }

    private static Field findField(Class<?> type, String name) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try { return current.getDeclaredField(name); }
            catch (NoSuchFieldException ignored) { }
        }
        throw new AssertionError("Missing field " + name + " on " + type.getName());
    }

    private static Unsafe unsafe() {
        try {
            Field field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            return (Unsafe) field.get(null);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }
}
