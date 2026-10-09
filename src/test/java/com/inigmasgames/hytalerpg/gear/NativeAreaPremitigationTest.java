package com.inigmasgames.hytalerpg.gear;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeAreaPremitigationTest {
    @Test void deadlyUsesTheSameSharedProducerCoefficientBeforeNativeApply(){
        var suite=new GearAffixQaSuite(GearCatalog.load());
        var item=suite.preview(suite.fixtures().stream()
                .filter(f->f.fixtureId().equals("ab-wa-136-affixed")).findFirst().orElseThrow());
        var snapshot=new GearEffectSnapshot(List.of(item));
        double chance=snapshot.forItem(item.identity()).value("WA-136")/100;
        assertTrue(chance>0);
        var runtime=new GearSignatureProcRuntime(()->chance*.75);
        UUID world=UUID.fromString("00000000-0000-0000-0000-000000000031");
        UUID actor=UUID.fromString("00000000-0000-0000-0000-000000000032");
        var shared=GearCombatEffects.attack(snapshot,item.identity(),"area/shared",100,1,
                true,false,0,null,0,1.5,false);
        var full=GearCombatEffects.attack(snapshot,item.identity(),"area/full",100,1,
                true,false,0,null,0,1.5,false);
        try{
            var acceptedShared=NativeGearAttackAcceptance.commit(world,actor,shared,.5,true,"area",runtime);
            var acceptedFull=NativeGearAttackAcceptance.commit(world,actor,full,1,true,"area",runtime);
            assertSame(shared,acceptedShared);
            assertEquals(200,acceptedFull.amount(GearCombatEffects.Channel.PHYSICAL),1e-9);
            assertEquals(.5,NativeGearAttackAcceptance.find(world,actor,acceptedShared).procCoefficient(),1e-9);
        }finally{NativeGearAttackAcceptance.clearWorld(world);}
    }
}
