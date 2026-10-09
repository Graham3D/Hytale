package com.inigmasgames.hytalerpg.gear;

import com.hypixel.hytale.component.ComponentRegistry;
import com.hypixel.hytale.component.system.ISystem;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeAffixDurabilityInstallationTest {
    static class OriginalArmor implements ISystem<Object> {}
    static class OriginalWeapon implements ISystem<Object> {}
    static class AffixArmor implements ISystem<Object> {}
    static class AffixWeapon implements ISystem<Object> {}
    static class FailingWeapon implements ISystem<Object> {
        @Override public void onSystemRegistered(){throw new IllegalStateException("injected registration failure");}
    }
    @Test void exactlyOneWriterPerProducerAndExactInstancesRestored() {
        var registry=new ComponentRegistry<Object>();
        var armor=new OriginalArmor();var weapon=new OriginalWeapon();
        registry.registerSystem(armor);registry.registerSystem(weapon);
        var replacement=NativeAffixDurabilityInstallation.Replacement.install(registry,
                List.of(armor,weapon),List.of(new AffixArmor(),new AffixWeapon()));
        assertFalse(registry.hasSystem(armor));assertFalse(registry.hasSystem(weapon));
        assertTrue(registry.hasSystemClass(AffixArmor.class));assertTrue(registry.hasSystemClass(AffixWeapon.class));
        replacement.close();replacement.close();
        assertTrue(registry.hasSystem(armor));assertTrue(registry.hasSystem(weapon));
        assertFalse(registry.hasSystemClass(AffixArmor.class));assertFalse(registry.hasSystemClass(AffixWeapon.class));
    }
    @Test void missingProducerLeavesTheStockWriterRegistered() {
        var registry=new ComponentRegistry<Object>();var armor=new OriginalArmor();registry.registerSystem(armor);
        assertThrows(IllegalStateException.class,()->NativeAffixDurabilityInstallation.Replacement.install(registry,
                List.of(armor,new OriginalWeapon()),List.of(new AffixArmor(),new AffixWeapon())));
        assertTrue(registry.hasSystem(armor));assertFalse(registry.hasSystemClass(AffixArmor.class));
    }
    @Test void failedReplacementRestoresBothOriginalWriters() {
        var registry=new ComponentRegistry<Object>();var armor=new OriginalArmor();var weapon=new OriginalWeapon();
        registry.registerSystem(armor);registry.registerSystem(weapon);
        assertThrows(IllegalStateException.class,()->NativeAffixDurabilityInstallation.Replacement.install(registry,
                List.of(armor,weapon),List.of(new AffixArmor(),new FailingWeapon())));
        assertTrue(registry.hasSystem(armor));assertTrue(registry.hasSystem(weapon));
        assertFalse(registry.hasSystemClass(AffixArmor.class));assertFalse(registry.hasSystemClass(FailingWeapon.class));
    }
}
