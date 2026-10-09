package com.inigmasgames.hywind;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Hytale validates SystemDependency targets as each system is registered. */
class RpgSystemRegistrationOrderTest {
    private static final String SOURCE = "src/main/java/com/inigmasgames/hywind/HyArpgPlugin.java";

    @Test void dependencyTargetsExistBeforeRpgSystemsAreRegistered() throws Exception {
        String source = Files.readString(Path.of(SOURCE));
        before(source, "registerSystem(gearEquipment.new Use())", "registerSystem(new com.inigmasgames.hytalerpg.gear.ManagedCarrierProjectile.Impact())");
        before(source, "registerSystem(gearEquipment.new Use())", "registerSystem(new com.inigmasgames.hytalerpg.gear.ManagedGearProjectile.Impact())");
        before(source, "registerSystem(gearEquipment.new Use())", "registerSystem(new com.inigmasgames.hytalerpg.gear.NativeGearAttackAcceptance.Prechain())");
        before(source, "registerSystem(gearEquipment.new Tick())", "registerSystem(itemAffixes.availabilityTick())");
        before(source, "registerSystem(skillExecutionSystem)", "registerSystem(itemAffixes.availabilityTick())");
        before(source, "registerSystem(skillExecutionSystem)", "registerSystem(supportSystem)");
        before(source, "registerSystem(new HytaleDamageLifecycleSystems.Gather(combatTrace,combatKernel.statuses(),difficultyCombat.healthBars()))", "registerSystem(new com.inigmasgames.hytalerpg.combat.hytale.HytaleDamageAdapter.ManagedGearGather())");
        before(source, "registerSystem(new HytaleDamageLifecycleSystems.Gather(combatTrace,combatKernel.statuses(),difficultyCombat.healthBars()))", "registerSystem(new com.inigmasgames.hytalerpg.combat.hytale.HytaleConditionalDamage.GearGather(");
        before(source, "registerSystem(new com.inigmasgames.hytalerpg.combat.hytale.HytaleDamageAdapter.ManagedGearGather())", "registerSystem(new com.inigmasgames.hytalerpg.combat.hytale.HytaleConditionalDamage.GearGather(");
        before(source, "registerSystem(new HytaleDamageLifecycleSystems.Filter(combatTrace))", "registerSystem(new com.inigmasgames.hytalerpg.execution.hytale.HytaleSupportSystem.Absorb(supportSystem))");
        before(source, "registerSystem(new com.inigmasgames.hytalerpg.execution.hytale.HytaleSupportSystem.Absorb(supportSystem))", "registerSystem(new com.inigmasgames.hytalerpg.execution.hytale.SupportDamageSystems.Shield(supportSystem))");
        before(source, "registerSystem(new com.inigmasgames.hytalerpg.execution.hytale.SupportDamageSystems.Shield(supportSystem))", "registerSystem(new com.inigmasgames.hytalerpg.execution.hytale.SupportDamageSystems.HealthCap(supportSystem))");
        before(source, "registerSystem(new com.inigmasgames.hytalerpg.execution.hytale.SupportDamageSystems.Shield(supportSystem))", "registerSystem(new com.inigmasgames.hytalerpg.combat.hytale.HytaleDamageAdapter.GearResistanceFilter())");
        before(source, "registerSystem(new com.inigmasgames.hytalerpg.combat.hytale.HytaleDamageAdapter.GearResistanceFilter())", "registerSystem(new HytaleDamageLifecycleSystems.BeforeAbsorption())");
        before(source, "registerSystem(new HytaleDamageLifecycleSystems.Filter(combatTrace))", "registerSystem(new com.inigmasgames.hytalerpg.execution.hytale.NativeStatusPhysicalMiss(combatKernel.statuses()))");
        before(source, "registerSystem(new HytaleDamageLifecycleSystems.Filter(combatTrace))", "registerSystem(new com.inigmasgames.hytalerpg.execution.hytale.SupportDamageSystems.Shield(supportSystem))");
        before(source, "registerSystem(new com.inigmasgames.hytalerpg.execution.hytale.SupportDamageSystems.HealthCap(supportSystem))", "registerSystem(new HytaleDamageLifecycleSystems.BeforeAbsorption())");
        before(source, "registerSystem(new HytaleDamageLifecycleSystems.BeforeAbsorption())", "registerSystem(gearSignatures.defenseFilter())");
        before(source, "registerSystem(new com.inigmasgames.hytalerpg.execution.hytale.SupportDamageSystems.Shield(supportSystem))", "registerSystem(gearSignatures.defenseFilter())");
        before(source, "registerSystem(new com.inigmasgames.hytalerpg.execution.hytale.HytaleSupportSystem.Absorb(supportSystem))", "registerSystem(new HytaleDamageLifecycleSystems.BeforeApplication())");
        before(source, "registerSystem(new com.inigmasgames.hytalerpg.execution.hytale.SupportDamageSystems.Shield(supportSystem))", "registerSystem(new HytaleDamageLifecycleSystems.BeforeApplication())");
        before(source, "registerSystem(new com.inigmasgames.hytalerpg.combat.hytale.HytaleDamageAdapter.ManagedGearGather())", "registerSystem(gearStatuses.nativeAcceptance())");
        before(source, "registerSystem(new com.inigmasgames.hytalerpg.execution.hytale.SupportDamageSystems.Shield(supportSystem))", "registerSystem(new com.inigmasgames.hytalerpg.execution.hytale.SupportDamageSystems.BeforeApply())");
        before(source, "registerSystem(new com.inigmasgames.hytalerpg.execution.hytale.HytaleSupportSystem.Absorb(supportSystem))", "registerSystem(new com.inigmasgames.hytalerpg.execution.hytale.SupportDamageSystems.BeforeApply())");
        before(source, "registerSystem(sharedReflection)", "registerSystem(new com.inigmasgames.hytalerpg.execution.hytale.HytaleRetaliationSystem(skillExecutionSystem))");
        before(source, "registerSystem(sharedReflection)", "registerSystem(new com.inigmasgames.hytalerpg.execution.hytale.HytaleEncounterRewards.Tracking(encounterRewards))");
        before(source, "registerSystem(sharedReflection)", "registerSystem(new com.inigmasgames.hytalerpg.execution.hytale.HytaleEncounterRewards.PlayerInjuries(encounterRewards))");
        before(source, "registerSystem(new com.inigmasgames.hytalerpg.execution.hytale.SupportDamageSystems.HealthCap(supportSystem))", "registerSystem(difficultyCombat.new Resistance())");
        before(source, "registerSystem(supportSystem)", "registerSystem(persistenceReady)");
        before(source, "registerSystem(skillExecutionSystem)", "registerSystem(persistenceReady)");
    }

    private static void before(String source, String prerequisite, String dependent) {
        assertEquals(1, source.split(java.util.regex.Pattern.quote(prerequisite), -1).length - 1, prerequisite);
        assertEquals(1, source.split(java.util.regex.Pattern.quote(dependent), -1).length - 1, dependent);
        assertTrue(source.indexOf(prerequisite) < source.indexOf(dependent), prerequisite + " must be registered before " + dependent);
    }
}
