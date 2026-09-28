package com.inigmasgames.hytalerpg.ui.inventory;

import org.junit.jupiter.api.Test;
import java.util.EnumSet;
import static org.junit.jupiter.api.Assertions.*;

class SpatialRoutePolicyTest {
    @Test void everyDeclaredRouteHasAnExplicitDecisionAndIncompleteReleaseFailsClosed() {
        assertEquals(EnumSet.allOf(SpatialRoutePolicy.Route.class), SpatialRoutePolicy.current().keySet());
        assertTrue(SpatialRoutePolicy.current().values().stream().allMatch(
                decision -> decision.evidence() != null && !decision.evidence().isBlank()));
        assertThrows(IllegalStateException.class, SpatialRoutePolicy::requireReleaseReady);
    }
}
