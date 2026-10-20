package com.inigmasgames.hytalerpg.readypath;

import com.inigmasgames.hytalerpg.commands.RpgReadyPathCommand;
import com.inigmasgames.hywind.readypath.ReadyPathProbe;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ReadyPathNativeCommandTest {
    @Test void operationalInspectionRequiresExplicitPermission() {
        assertEquals("inigmasgames.rpg.readypath", new RpgReadyPathCommand(null).getPermission());
    }
    @Test void missingClientEvidenceIsNeverReportedAsReadyOrZeroLatency() {
        var status = ReadyPathProbe.inspect(UUID.randomUUID());
        assertEquals("UNKNOWN", status.get("clientClickToPlayMs"));
        assertEquals("UNKNOWN", status.get("fullGameplayReady"));
        assertEquals("UNKNOWN", status.get("nativeReadyObserved"));
    }
}
