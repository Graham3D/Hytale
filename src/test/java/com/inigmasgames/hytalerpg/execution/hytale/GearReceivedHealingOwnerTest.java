package com.inigmasgames.hytalerpg.execution.hytale;

import com.inigmasgames.hytalerpg.combat.attribute.RpgAttribute;
import com.inigmasgames.hytalerpg.combat.resource.NativeResourcePort;
import com.inigmasgames.hytalerpg.combat.resource.ResourceType;
import com.inigmasgames.hytalerpg.gear.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class GearReceivedHealingOwnerTest {
    private static GearEffectSnapshot recipient(boolean rolled,boolean intact){
        var qa=new GearAffixQaSuite(GearCatalog.load());
        var item=qa.preview(qa.fixtures().stream().filter(f->f.fixtureId().equals("ab-wa-111-affixed"))
                .findFirst().orElseThrow());
        if(!rolled)item=new GearInstance(item.schemaVersion(),UUID.randomUUID(),item.definitionRevision(),item.baseId(),
                item.baseName(),item.category(),item.sourceEra(),item.itemLevel(),item.rarity(),
                item.intrinsicThousandths(),item.intrinsicStats(),item.requirements(),List.of(),item.rngVersion(),true);
        var attributes=new EnumMap<RpgAttribute,Integer>(RpgAttribute.class);
        for(var attribute:RpgAttribute.values())attributes.put(attribute,500);
        var result=GearEquipmentResolution.resolve(99,attributes,
                List.of(new GearEquipmentResolution.Candidate(item,true,intact,true)));
        assertEquals(intact?List.of(item):List.of(),result.validItems());
        return result.effects().snapshot();
    }
    private static final class HealthPort implements NativeResourcePort {
        double value;
        HealthPort(double value){this.value=value;}
        public double current(ResourceType type){return value;}
        public double maximum(ResourceType type){return 100;}
        public void setCurrent(ResourceType type,double amount){value=amount;}
    }
    @Test void wa111AdmittedPlayerRecipientChangesActualRequestAndClipsNativeCredit(){
        var rolled=recipient(true,true);var unrolled=recipient(false,true);var broken=recipient(true,false);
        assertTrue(rolled.value("WA-111")>0);
        double base=20,request=HytaleSupportSystem.receivedHealing(base,rolled);
        assertTrue(request>base);
        assertEquals(base*(1+rolled.percent(GearEffectSnapshot.Operator.HEALING_RECEIVED)),request,1e-9);
        assertEquals(base,HytaleSupportSystem.receivedHealing(base,unrolled),1e-9);
        assertEquals(base,HytaleSupportSystem.receivedHealing(base,broken),1e-9);
        var treated=new HealthPort(70);var control=new HealthPort(70);var denied=new HealthPort(70);
        double gain=treated.restoreResourceAtMost(ResourceType.HEALTH,request,100);
        double ordinary=control.restoreResourceAtMost(ResourceType.HEALTH,
                HytaleSupportSystem.receivedHealing(base,unrolled),100);
        denied.restoreResourceAtMost(ResourceType.HEALTH,HytaleSupportSystem.receivedHealing(base,broken),100);
        assertEquals(request,gain,1e-9);
        assertEquals(base,ordinary,1e-9);
        assertEquals(control.value,denied.value,1e-9);
        var capped=new HealthPort(99);
        assertEquals(1,capped.restoreResourceAtMost(ResourceType.HEALTH,request,100),1e-9);
        assertEquals(100,capped.value,1e-9);
    }
}
