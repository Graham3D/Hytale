package com.inigmasgames.persistentnpcs;

import com.inigmasgames.persistentnpcs.orbis.OrbisReadinessService;
import com.inigmasgames.persistentnpcs.orbis.OrbisReadinessStatus;
import com.inigmasgames.persistentnpcs.orbis.OrbisReadinessSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

/** Deterministic readiness-model and native HUD contract checks. */
public final class R066ReadinessHudTest {
    private R066ReadinessHudTest() { }

    public static void main(String[] arguments) throws Exception {
        readinessTransitionsAreAtomicAndTruthful();
        nativeHudContractIsStyledAndComplete();
        System.out.println("R066 native readiness HUD tests passed.");
    }

    private static void readinessTransitionsAreAtomicAndTruthful() throws Exception {
        OrbisReadinessService service = new OrbisReadinessService();
        assert service.snapshot().rows().size() == 4;
        assert service.snapshot().row(OrbisReadinessSystem.MOONSHINE)
                .status() == OrbisReadinessStatus.NOT_STARTED;
        AtomicInteger notifications = new AtomicInteger();
        AutoCloseable subscription = service.subscribe(ignored -> notifications.incrementAndGet());
        service.transition(OrbisReadinessSystem.MOONSHINE, 73,
                OrbisReadinessStatus.WARMING, "real warm inference");
        var warming = service.snapshot().row(OrbisReadinessSystem.MOONSHINE);
        assert warming.readinessPercent() == 73;
        assert warming.filledPips() == 7;
        assert notifications.get() == 1;
        service.fail(OrbisReadinessSystem.MOONSHINE,
                OrbisReadinessStatus.ERROR, "worker failed");
        var failed = service.snapshot().row(OrbisReadinessSystem.MOONSHINE);
        assert failed.readinessPercent() == 73 : "failure must preserve truthful progress";
        assert failed.status() == OrbisReadinessStatus.ERROR;
        subscription.close();
        service.transition(OrbisReadinessSystem.ORBIS, 150,
                OrbisReadinessStatus.READY, "ready");
        assert notifications.get() == 2 : "closed listener received another transition";
        assert service.snapshot().row(OrbisReadinessSystem.ORBIS).filledPips() == 10;
        service.close();
    }

    private static void nativeHudContractIsStyledAndComplete() throws Exception {
        String ui = Files.readString(Path.of("src/main/resources/Common/UI/Custom/Hud/"
                + "ImmersiveNPCsRevision.ui"));
        for (String system : new String[] { "Moonshine", "Nemotron", "Chatterbox", "Orbis" }) {
            assert ui.contains("#" + system + "Name");
            assert ui.contains("#" + system + "Pip1");
            assert ui.contains("#" + system + "Pip10");
            assert ui.contains("#" + system + "Status");
        }
        assert ui.contains("RenderBold: true");
        assert ui.contains("TextColor: #FFFFFF");
        assert ui.contains("../Common/DropdownCaret.png");
        assert !ui.contains("●") && !ui.contains("○");
        String hud = Files.readString(Path.of("src/main/java/com/inigmasgames/"
                + "persistentnpcs/PersistentNpcsHud.java"));
        assert hud.contains("case READY -> \"#73E08C\"") : "READY text must be green";
        assert hud.contains("readiness.subscribe") : "HUD must react to cached transitions";
        assert !hud.contains("scheduleWithFixedDelay") : "HUD must not poll providers";
    }
}
