package com.inigmasgames.hytalerpg.combat.hytale;

import com.inigmasgames.hytalerpg.combat.attribute.RpgAttribute;
import com.inigmasgames.hytalerpg.combat.resource.GearRecoveryRuntime;
import com.inigmasgames.hytalerpg.combat.resource.NativeResourcePort;
import com.inigmasgames.hytalerpg.combat.resource.ResourceType;
import com.inigmasgames.hytalerpg.gear.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class GearAppliedHitRecoveryOwnerTest {
    private static final GearAffixQaSuite QA=new GearAffixQaSuite(GearCatalog.load());

    static GearEffectSnapshot admitted(String id,boolean rolled){
        var item=QA.preview(QA.fixtures().stream().filter(f->f.fixtureId().equals("ab-"+id.toLowerCase(Locale.ROOT)+"-affixed"))
                .findFirst().orElseThrow());
        if(!rolled)item=new GearInstance(item.schemaVersion(),UUID.randomUUID(),item.definitionRevision(),item.baseId(),
                item.baseName(),item.category(),item.sourceEra(),item.itemLevel(),item.rarity(),
                item.intrinsicThousandths(),item.intrinsicStats(),item.requirements(),List.of(),item.rngVersion(),true);
        var attributes=new EnumMap<RpgAttribute,Integer>(RpgAttribute.class);
        for(var attribute:RpgAttribute.values())attributes.put(attribute,500);
        var result=GearEquipmentResolution.resolve(99,attributes,List.of(new GearEquipmentResolution.Candidate(item,true,true,true)));
        assertEquals(List.of(item),result.validItems(),id);
        return result.effects().snapshot();
    }
    private static final class Port implements NativeResourcePort,GearRecoveryRuntime.Credit {
        double health=900,mana=400;
        public double current(ResourceType type){return type==ResourceType.HEALTH?health:mana;}
        public double maximum(ResourceType type){return type==ResourceType.HEALTH?1000:500;}
        public void setCurrent(ResourceType type,double value){if(type==ResourceType.HEALTH)health=value;else mana=value;}
        public double admit(GearRecoveryRuntime.Pool pool,double requested,double cap){
            return restoreResourceAtMost(pool==GearRecoveryRuntime.Pool.HIT_MANA?ResourceType.MANA:ResourceType.HEALTH,requested,cap);
        }
    }
    private static GearAppliedHitRecovery owner(GearRecoveryRuntime runtime){
        return new GearAppliedHitRecovery(runtime,new GearAppliedHitRecovery.Eligibility(){
            public boolean hostile(com.hypixel.hytale.component.Ref<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> source,
                                   com.hypixel.hytale.component.Ref<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> target,
                                   com.hypixel.hytale.component.CommandBuffer<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> buffer){return true;}
            public double normalHealthMaximum(UUID actor,com.hypixel.hytale.component.Ref<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> source,
                                   com.hypixel.hytale.component.CommandBuffer<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> buffer){return 1000;}
            public double spendableManaMaximum(UUID actor,com.hypixel.hytale.component.Ref<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> source,
                                   com.hypixel.hytale.component.CommandBuffer<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> buffer){return 500;}
        });
    }
    private static HytaleDamageLifecycleSystems.AppliedReceipt receipt(UUID actor,UUID victim,String root,
            GearEffectSnapshot gear,boolean cancelled,boolean blocked,HytaleDamageMetadata.Origin origin){
        var hit=new GearCombatEffects.Hit(gear.items().getFirst().identity(),gear.revision(),root,
                Map.of(),Map.of(),Map.of(),false,1,gear,null);
        var metadata=new HytaleDamageMetadata(actor,root,"skill",root+"/contact",100,100,root+"/effect",true,origin);
        return new HytaleDamageLifecycleSystems.AppliedReceipt(metadata,
                new HytaleDamageAdapter.GearHitSource(hit,root+"/contact",GearCombatEffects.Channel.PHYSICAL),
                victim,100,70,30,cancelled,blocked);
    }
    private static void hit(String id,GearRecoveryRuntime.Pool pool){
        var gear=admitted(id,true);var control=admitted(id,false);
        UUID actor=UUID.randomUUID(),victim=UUID.randomUUID();String world=UUID.randomUUID().toString();
        var runtime=new GearRecoveryRuntime();var callback=owner(runtime);var port=new Port();
        var applied=receipt(actor,victim,"root",gear,false,false,HytaleDamageMetadata.Origin.DIRECT);
        double now=System.nanoTime()/1e9;
        callback.accepted(applied,actor,world,false,null,1000,500,now);
        callback.accepted(applied,actor,world,false,null,1000,500,now);
        double expected=(id.equals("WA-094")||id.equals("WA-095"))?gear.value(id):30*gear.percent(id);
        expected=Math.min(expected,pool==GearRecoveryRuntime.Pool.HIT_MANA?10:40);
        assertTrue(expected>0,id);
        assertEquals(expected,runtime.pay(actor,world,pool,pool==GearRecoveryRuntime.Pool.HIT_MANA?500:1000,
                now,port).amount(),1e-8,id);
        assertEquals(pool==GearRecoveryRuntime.Pool.HIT_MANA?900:400,
                pool==GearRecoveryRuntime.Pool.HIT_MANA?port.health:port.mana,1e-8,id);
        assertEquals(expected,pool==GearRecoveryRuntime.Pool.HIT_MANA?port.mana-400:port.health-900,1e-8,id);
        assertEquals(0,runtime.pay(actor,world,pool,pool==GearRecoveryRuntime.Pool.HIT_MANA?500:1000,
                now+1.1,port).amount(),id);

        var blank=new GearRecoveryRuntime();var blankPort=new Port();
        owner(blank).accepted(receipt(actor,victim,"control",control,false,false,HytaleDamageMetadata.Origin.DIRECT),
                actor,world,false,null,1000,500,now);
        assertEquals(0,blank.pay(actor,world,pool,1000,now,blankPort).amount(),id);
        for(int mode=0;mode<3;mode++){
            var rejected=new GearRecoveryRuntime();var rejectedPort=new Port();
            owner(rejected).accepted(receipt(actor,victim,"reject"+mode,gear,mode==0,mode==1,
                    mode==2?HytaleDamageMetadata.Origin.TRIGGERED:HytaleDamageMetadata.Origin.DIRECT),
                    actor,world,false,null,1000,500,now);
            assertEquals(0,rejected.pay(actor,world,pool,1000,now,rejectedPort).amount(),id+" control "+mode);
        }
    }
    @Test void wa094AcceptedPostMitigationHitCreditsHealthOnce(){hit("WA-094",GearRecoveryRuntime.Pool.HIT_HEALTH);}
    @Test void wa095AcceptedPostMitigationHitCreditsManaOnce(){hit("WA-095",GearRecoveryRuntime.Pool.HIT_MANA);}
    @Test void wa096AcceptedPostMitigationHitCreditsLifeLeechOnce(){hit("WA-096",GearRecoveryRuntime.Pool.HIT_HEALTH);}
    @Test void wa097AcceptedPostMitigationHitCreditsManaLeechOnce(){hit("WA-097",GearRecoveryRuntime.Pool.HIT_MANA);}
}
