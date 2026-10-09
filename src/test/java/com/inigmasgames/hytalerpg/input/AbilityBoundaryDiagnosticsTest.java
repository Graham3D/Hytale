package com.inigmasgames.hytalerpg.input;

import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.packets.interaction.SyncInteractionChain;
import com.hypixel.hytale.protocol.packets.interaction.SyncInteractionChains;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AbilityBoundaryDiagnosticsTest {
    @Test void detailedPacketObservationCannotCastAndCallbackStillDeduplicates() {
        var actor = UUID.randomUUID();
        var adapter = new HytaleAbilitySkillInputAdapter();
        var enabled = new AtomicBoolean();
        var rows = new ArrayList<Map<String,Object>>();
        adapter.useNativeExecution();
        adapter.configureDiagnostics(enabled::get, (id, row) -> rows.add(row));
        var chain = new SyncInteractionChain();
        chain.initial = true; chain.chainId = 42; chain.interactionType = InteractionType.Ability2;
        chain.itemInHandId = "RPG_Gear_battleaxe_adamantite_n_Rare";
        var packet = new SyncInteractionChains(); packet.updates = new SyncInteractionChain[]{chain};
        adapter.observe(actor, packet); assertTrue(rows.isEmpty());
        enabled.set(true); adapter.observe(actor, packet);
        assertEquals("INBOUND_ABILITY", rows.getFirst().get("boundary"));
        assertEquals(chain.itemInHandId, rows.getFirst().get("heldItem"));
        assertEquals(0, adapter.drainFor(actor, request -> fail("Observer cannot execute"), 8));
        var nativeIdentity = new Object();
        adapter.acceptNativeExecution(actor, InteractionType.Ability2, 42, nativeIdentity, "RPG_Ability_Iron_Sentinel", 0);
        adapter.acceptNativeExecution(actor, InteractionType.Ability2, 42, nativeIdentity, "RPG_Ability_Iron_Sentinel", 0);
        assertEquals(1, adapter.drainFor(actor, request -> {}, 8));
        assertTrue(rows.stream().anyMatch(row -> row.get("boundary").equals("NATIVE_CALLBACK")));
    }
    @Test void readinessRecordsChangesAndReenableWithoutWritingEveryTick() {
        var adapter = new HytaleAbilitySkillInputAdapter();
        var actor = UUID.randomUUID(); var enabled = new AtomicBoolean(true);
        var rows = new ArrayList<Map<String,Object>>();
        adapter.configureDiagnostics(enabled::get, (id, row) -> rows.add(row));
        for (int i=0;i<100;i++) adapter.diagnosticReadiness(actor, "", false, "RPG_Ability_Iron_Sentinel", "");
        assertEquals(1, rows.size());
        adapter.diagnosticReadiness(actor, "Weapon_Sword", true, "RPG_Ability_Iron_Sentinel", "");
        assertEquals(2, rows.size());
        assertEquals(true, rows.getLast().get("nativeWeaponEligible"));
        enabled.set(false); adapter.diagnosticReadiness(actor, "Weapon_Sword", true, "RPG_Ability_Iron_Sentinel", "");
        enabled.set(true); adapter.diagnosticReadiness(actor, "Weapon_Sword", true, "RPG_Ability_Iron_Sentinel", "");
        assertEquals(3, rows.size());
        adapter.clear(actor); adapter.diagnosticReadiness(actor, "", false, "", "");
        assertEquals(4, rows.size());
    }
}
