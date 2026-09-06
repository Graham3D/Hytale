package com.inigmasgames.hytalerpg.combat.resource;

import com.inigmasgames.hytalerpg.combat.balance.CombatBalanceProfile;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Pure timing state used by a production WildernessTracker adapter. */
public final class HomeRestorationService {
    private final CombatBalanceProfile profile;
    private final Map<UUID, Double> secondsAtHome = new HashMap<>();
    public HomeRestorationService(CombatBalanceProfile profile) { this.profile = profile; }
    public boolean observe(UUID actor, boolean wildernessTrackerSaysHome, double secondsOutOfHostileCombat,
                           double elapsedSeconds, RpgResourceService resources, NativeResourcePort port) {
        double accumulated = wildernessTrackerSaysHome ? secondsAtHome.getOrDefault(actor, 0.0) + Math.max(0.0, elapsedSeconds) : 0.0;
        secondsAtHome.put(actor, accumulated);
        if (accumulated < profile.homeSeconds || secondsOutOfHostileCombat < profile.outOfHostileCombatSeconds) return false;
        resources.restoreHome(actor, port);
        return true;
    }
    public void clear(UUID actor) { secondsAtHome.remove(actor); }
}
