package com.inigmasgames.hytalerpg.ui.hud;

import com.hypixel.hytale.protocol.packets.interface_.HudComponent;

import java.util.EnumSet;
import java.util.Set;

/** Exact native HUD visibility snapshot/restore lease. */
public final class HudVisibilityLease {
    private final Port port;
    private final Set<HudComponent> snapshot;
    private boolean restored;

    private HudVisibilityLease(Port port, Set<HudComponent> snapshot) {
        this.port = port; this.snapshot = Set.copyOf(snapshot);
    }

    public static HudVisibilityLease hideRpgResourceDuplicates(Port port) {
        Set<HudComponent> before = port.visible();
        HudVisibilityLease lease = new HudVisibilityLease(port, before);
        EnumSet<HudComponent> after = before.isEmpty()
                ? EnumSet.noneOf(HudComponent.class) : EnumSet.copyOf(before);
        after.remove(HudComponent.Mana);
        after.remove(HudComponent.Health);
        after.remove(HudComponent.Stamina);
        try { port.setVisible(Set.copyOf(after)); }
        catch (RuntimeException error) {
            port.setVisible(lease.snapshot);
            throw error;
        }
        return lease;
    }

    public synchronized void restore() {
        if (restored) return;
        port.setVisible(snapshot);
        restored = true;
    }

    public Set<HudComponent> snapshot() { return snapshot; }
    public boolean restored() { return restored; }

    public interface Port {
        Set<HudComponent> visible();
        void setVisible(Set<HudComponent> components);
    }
}
