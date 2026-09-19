package com.inigmasgames.persistentnpcs;

import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBinding;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.inigmasgames.persistentnpcs.appearance.NpcAppearanceCatalogService.Category;
import com.inigmasgames.persistentnpcs.appearance.NpcAppearanceCatalogService.PrimaryCategory;
import com.inigmasgames.persistentnpcs.appearance.NpcAppearanceDraft;
import com.inigmasgames.persistentnpcs.authoring.NpcAuthoringEventEnvelope;
import com.inigmasgames.persistentnpcs.authoring.NpcAuthoringSession;
import com.inigmasgames.persistentnpcs.authoring.NpcAuthoringSessionRegistry;
import com.inigmasgames.persistentnpcs.ui.AppearanceUiState;
import com.inigmasgames.persistentnpcs.ui.NpcProfilePage;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import sun.misc.Unsafe;

/** Checkpoint 1 regressions for the R156 Appearance event lifecycle repair. */
public final class R156AppearanceInteractionLifecycleTest {
    private static final Unsafe UNSAFE = unsafe();

    private R156AppearanceInteractionLifecycleTest() { }

    public static void main(String[] arguments) throws Exception {
        explicitBindingLockPolicy();
        actualHandlerAcknowledgesNoopAndRejectedCurrentPageEvents();
        actualHandlerDoesNotAcknowledgeReplacedPageEvents();
        sourceAndResourceBoundaryRemainNarrow();
        System.out.println("R156 PASS: Appearance interactions are non-locking, no-op/rejected current-page events acknowledge, and replaced-page events cannot affect a newer page.");
    }

    private static void explicitBindingLockPolicy() throws Exception {
        Fixture fixture = fixture();
        try {
            UIEventBuilder events = new UIEventBuilder();
            invoke(fixture.page, "bindAppearanceEditorEvents",
                    new Class<?>[] { UIEventBuilder.class }, events);

            int appearanceBindings = 0;
            boolean searchUsesSdkOutputKey = false;
            for (CustomUIEventBinding binding : events.getEvents()) {
                if (binding.data == null || !binding.data.contains("APPEARANCE_")) continue;
                appearanceBindings++;
                boolean transition = binding.data.contains("APPEARANCE_CANCEL")
                        || binding.data.contains("APPEARANCE_SAVE");
                assert binding.locksInterface == transition
                        : "Unexpected lock policy for " + binding.selector + " data=" + binding.data;
                searchUsesSdkOutputKey |= binding.data.contains("@AppearanceSearch")
                        && binding.data.contains("#AppearanceSearchInput.Value");
            }
            assert appearanceBindings > 10 : "Expected the real production appearance bindings";
            assert searchUsesSdkOutputKey
                    : "Client-derived search text must use Hytale's @ output-binding contract";
        } finally {
            fixture.session.close();
        }
    }

    private static void actualHandlerAcknowledgesNoopAndRejectedCurrentPageEvents()
            throws Exception {
        Fixture fixture = fixture();
        try {
            fixture.page.handleDataEvent(null, null,
                    event(fixture.session, "APPEARANCE_PRIMARY", data ->
                            setReflective(data, "appearancePrimary", "BODY")));
            assert traceCount(fixture.logs, "APPEARANCE_EVENT_RECEIVED") == 1;
            assert traceCount(fixture.logs, "APPEARANCE_EVENT_NOOP") == 1;
            assert traceCount(fixture.logs, "APPEARANCE_UPDATE_SENT") == 1;
            assert last(fixture.logs, "APPEARANCE_EVENT_RECEIVED").contains("locksInterface=false");
            assert last(fixture.logs, "APPEARANCE_UPDATE_SENT").contains("updateSent=true");

            fixture.logs.clear();
            fixture.page.handleDataEvent(null, null,
                    event(fixture.session, "APPEARANCE_OPTION", data -> {
                        setReflective(data, "appearanceCatalogHash", "stale-hash");
                        setReflective(data, "appearanceOptionId", "Any_Option");
                    }));
            assert traceCount(fixture.logs, "APPEARANCE_EVENT_RECEIVED") == 1;
            assert traceCount(fixture.logs, "APPEARANCE_EVENT_REJECTED") == 1;
            assert traceCount(fixture.logs, "APPEARANCE_UPDATE_SENT") == 1
                    : "Rejected events from the current page must release any client wait";
            assert last(fixture.logs, "APPEARANCE_EVENT_REJECTED")
                    .contains("reason=\"STALE_CATALOG_HASH\"");
        } finally {
            fixture.session.close();
        }
    }

    private static void actualHandlerDoesNotAcknowledgeReplacedPageEvents() throws Exception {
        Fixture fixture = fixture();
        try {
            NpcProfilePage.PageData stale = event(fixture.session,
                    "APPEARANCE_PRIMARY", data ->
                            setReflective(data, "appearancePrimary", "HAIR"));
            setReflective(stale, "authoringPageGeneration",
                    Long.toString(fixture.session.pageGeneration() + 1));
            fixture.page.handleDataEvent(null, null, stale);
            assert traceCount(fixture.logs, "APPEARANCE_EVENT_RECEIVED") == 1;
            assert traceCount(fixture.logs, "APPEARANCE_EVENT_REJECTED") == 1;
            assert traceCount(fixture.logs, "APPEARANCE_UPDATE_SENT") == 0
                    : "A dismissed/replaced page event must never acknowledge into the newer page";
            assert last(fixture.logs, "APPEARANCE_EVENT_REJECTED")
                    .contains("currentAtReceive=false");
        } finally {
            fixture.session.close();
        }
    }

    private static void sourceAndResourceBoundaryRemainNarrow() throws Exception {
        String page = Files.readString(Path.of(
                "src/main/java/com/inigmasgames/persistentnpcs/ui/NpcProfilePage.java"));
        assert page.contains("APPEARANCE_EVENT_RECEIVED")
                && page.contains("APPEARANCE_EVENT_NOOP")
                && page.contains("APPEARANCE_UPDATE_SENT")
                && page.contains("APPEARANCE_PREVIEW_REQUESTED")
                && page.contains("APPEARANCE_PREVIEW_APPLIED")
                && page.contains("APPEARANCE_EVENT_REJECTED");
        assert page.contains("sendUpdate();")
                : "Diff-suppressed/no-op handlers require an explicit empty acknowledgement";
        assert page.contains("request(store.getExternalData().getWorld()::execute")
                : "Preview work must stay asynchronous and generation-gated";
        assert Files.notExists(Path.of("src/main/resources/appearance-color-sources"));
        assert Files.notExists(Path.of(
                "src/main/resources/Common/UI/Custom/Pages/ImmersiveNpcAppearance/Thumbnails"));
    }

    private static Fixture fixture() throws Exception {
        UUID viewer = UUID.randomUUID();
        UUID npc = UUID.randomUUID();
        List<String> logs = new ArrayList<>();
        NpcAuthoringSession session = NpcAuthoringSessionRegistry.shared().acquire(
                viewer, npc, null, Map.of("appearance", "test"), ignored -> true,
                logs::add);
        session.ready();
        long editorGeneration = session.openEditor(NpcAuthoringSession.EditorKind.APPEARANCE);

        NpcAppearanceDraft draft = (NpcAppearanceDraft) UNSAFE.allocateInstance(
                NpcAppearanceDraft.class);
        put(draft, "sessionId", session.sessionId());
        put(draft, "stableNpcId", session.npcStableId());
        putLong(draft, "editorGeneration", editorGeneration);

        NpcProfilePage page = (NpcProfilePage) UNSAFE.allocateInstance(NpcProfilePage.class);
        PlayerRef player = (PlayerRef) UNSAFE.allocateInstance(PlayerRef.class);
        put(page, "playerRef", player);
        put(page, "authoringSession", session);
        put(page, "appearanceDraft", draft);
        AppearanceUiState state = new AppearanceUiState();
        setReflective(state, "hashes", new AppearanceUiState.Hashes(
                "expected-hash", "selection", "color", "skin", "preview"));
        put(page, "appearanceUiState", state);
        put(page, "diagnostics", (java.util.function.Consumer<String>) logs::add);
        put(page, "appearancePrimary", PrimaryCategory.BODY);
        put(page, "appearanceCategory", Category.BODY_CHARACTERISTIC);
        put(page, "appearanceSearch", "");
        putBoolean(page, "built", true);
        logs.clear();
        return new Fixture(page, session, logs);
    }

    private static NpcProfilePage.PageData event(NpcAuthoringSession session,
            String action, ThrowingConsumer<NpcProfilePage.PageData> additional) throws Exception {
        NpcProfilePage.PageData data = new NpcProfilePage.PageData();
        setReflective(data, "authoringSchemaVersion",
                Integer.toString(NpcAuthoringEventEnvelope.CURRENT_SCHEMA_VERSION));
        setReflective(data, "authoringSessionId", session.sessionId().toString());
        setReflective(data, "authoringViewerPlayerId", session.viewerPlayerId().toString());
        setReflective(data, "authoringNpcStableId", session.npcStableId().toString());
        setReflective(data, "authoringPageGeneration", Long.toString(session.pageGeneration()));
        setReflective(data, "authoringEditor", NpcAuthoringSession.EditorKind.APPEARANCE.name());
        setReflective(data, "authoringEditorGeneration", Long.toString(session.editorGeneration()));
        setReflective(data, "authoringAction", action);
        additional.accept(data);
        return data;
    }

    private static long traceCount(List<String> logs, String marker) {
        return logs.stream().filter(line -> line.startsWith(marker + " ")).count();
    }

    private static String last(List<String> logs, String marker) {
        return logs.reversed().stream().filter(line -> line.startsWith(marker + " "))
                .findFirst().orElseThrow(() -> new AssertionError("Missing " + marker));
    }

    private static Object invoke(Object target, String name, Class<?>[] parameterTypes,
            Object... arguments) throws Exception {
        Method method = target.getClass().getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        return method.invoke(target, arguments);
    }

    private static void setReflective(Object target, String name, Object value) throws Exception {
        Field field = findField(target.getClass(), name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static void put(Object target, String name, Object value) {
        Field field = findField(target.getClass(), name);
        UNSAFE.putObject(target, UNSAFE.objectFieldOffset(field), value);
    }

    private static void putBoolean(Object target, String name, boolean value) {
        Field field = findField(target.getClass(), name);
        UNSAFE.putBoolean(target, UNSAFE.objectFieldOffset(field), value);
    }

    private static void putLong(Object target, String name, long value) {
        Field field = findField(target.getClass(), name);
        UNSAFE.putLong(target, UNSAFE.objectFieldOffset(field), value);
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

    private record Fixture(NpcProfilePage page, NpcAuthoringSession session,
            List<String> logs) { }

    @FunctionalInterface
    private interface ThrowingConsumer<T> { void accept(T value) throws Exception; }
}
