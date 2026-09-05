package com.inigmasgames.persistentnpcs;

import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBinding;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.inigmasgames.persistentnpcs.authoring.NpcAuthoringSession;
import com.inigmasgames.persistentnpcs.authoring.NpcAuthoringSessionRegistry;
import com.inigmasgames.persistentnpcs.ui.NpcProfilePage;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import sun.misc.Unsafe;

/** Connected regression for R167's invalid output-bound Profile section constants. */
public final class R168ProfileSectionEventHotfixTest {
    private static final Unsafe UNSAFE = unsafe();

    private R168ProfileSectionEventHotfixTest() { }

    public static void main(String[] arguments) throws Exception {
        profileSectionsAreEmbeddedConstantsNotClientOutputBindings();
        saveFieldsRemainClientOutputBindings();
        System.out.println("R168 PASS: all eight Profile section payloads are static constants; form values remain typed client output captures.");
    }

    private static void profileSectionsAreEmbeddedConstantsNotClientOutputBindings()
            throws Exception {
        NpcAuthoringSession session = NpcAuthoringSessionRegistry.shared().acquire(
                UUID.randomUUID(), UUID.randomUUID(), null, Map.of("profile", "test"),
                ignored -> true, ignored -> { });
        try {
            session.ready();
            session.openEditor(NpcAuthoringSession.EditorKind.PROFILE);
            NpcProfilePage page = (NpcProfilePage) UNSAFE.allocateInstance(NpcProfilePage.class);
            put(page, "authoringSession", session);

            UIEventBuilder events = new UIEventBuilder();
            invoke(page, "bindProfileEditorEvents",
                    new Class<?>[] { UIEventBuilder.class }, events);

            int sectionEvents = 0;
            for (CustomUIEventBinding binding : events.getEvents()) {
                String data = binding.data;
                if (data == null || !data.contains("PROFILE_SECTION")) continue;
                sectionEvents++;
                assert data.contains("ProfileSection")
                        : "Section identity must be embedded in the event: " + data;
                assert !data.contains("@ProfileSection")
                        : "Static section constants cannot use client output binding: " + data;
            }
            assert sectionEvents == 8 : "Expected one safe event for every Profile section";
        } finally {
            session.close();
        }
    }

    private static void saveFieldsRemainClientOutputBindings() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/inigmasgames/persistentnpcs/ui/NpcProfilePage.java"));
        assert source.contains(".append(\"ProfileSection\", category.name())");
        assert source.contains("new KeyedCodec<>(\"ProfileSection\", Codec.STRING)");
        assert !source.contains(".append(\"@ProfileSection\", category.name())");
        assert !source.contains("new KeyedCodec<>(\"@ProfileSection\", Codec.STRING)");
        assert source.contains(".append(\"@ProfileBiography\", \"#ProfileBiographyInput.Value\")")
                : "Save must still gather the live Biography value";
        assert source.contains("new KeyedCodec<>(\"@ProfileBiography\", Codec.STRING)")
                : "Save output binding must remain typed";
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
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                // Continue through the page superclass chain.
            }
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
