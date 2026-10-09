package com.inigmasgames.hytalerpg.gear;

import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.inigmasgames.hytalerpg.combat.hytale.HytaleDamageAdapter;
import com.inigmasgames.hytalerpg.combat.hytale.HytaleDamageMetadata;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeAffixRecipientFilterTest {
    private static Damage hit(String cause,HytaleDamageMetadata.Origin origin,float amount) {
        var damage=new Damage(Damage.NULL_SOURCE,DamageCause.getAssetMap().getAsset(cause),amount);
        if(origin!=null)damage.putMetaObject(HytaleDamageAdapter.RPG_METADATA,new com.google.gson.Gson().toJson(
                new HytaleDamageMetadata(UUID.randomUUID(),"root","skill","contact",amount,100,
                        "contact",true,origin)));
        return damage;
    }
    @Test void wa078NativeFilterWritesEachElementOnceAndLeavesHazardsImmunityAndOtherOwnersAlone() throws Exception {
        try(var assets=NativeAssetTestFixtures.open()) {
            var item=MasterAffixTestEquipment.fixture("WA-078",false);
            var accepted=GearEquipmentResolution.resolve(99,MasterAffixTestEquipment.BASELINE,
                    List.of(new GearEquipmentResolution.Candidate(item,true,true,true)));
            assertEquals(List.of(item),accepted.validItems());
            var defense=accepted.effects().snapshot();assertTrue(defense.percent("WA-078")>0);
            var plain=MasterAffixTestEquipment.accepted(MasterAffixTestEquipment.fixture("WA-078",true)).snapshot();
            for(var channel:GearCombatEffects.Channel.values())if(channel!=GearCombatEffects.Channel.PHYSICAL) {
                String cause=GearCombatEffects.nativeCause(channel);
                var positive=hit(cause,HytaleDamageMetadata.Origin.DIRECT,100);
                var control=hit(cause,HytaleDamageMetadata.Origin.DIRECT,100);
                HytaleDamageAdapter.GearResistanceFilter.applyResolved(positive,defense,true,()->0);
                HytaleDamageAdapter.GearResistanceFilter.applyResolved(control,plain,true,()->0);
                assertEquals(100*(1-defense.percent("WA-078")),positive.getAmount(),1e-5,cause);
                assertEquals(100,control.getAmount());
                float once=positive.getAmount();
                HytaleDamageAdapter.GearResistanceFilter.applyResolved(positive,defense,true,()->0);
                assertEquals(once,positive.getAmount());
                var immune=hit(cause,HytaleDamageMetadata.Origin.DIRECT,0);
                HytaleDamageAdapter.GearResistanceFilter.applyResolved(immune,defense,true,()->1);
                assertEquals(0,immune.getAmount());
                for(var origin:new HytaleDamageMetadata.Origin[]{null,HytaleDamageMetadata.Origin.PERIODIC,
                        HytaleDamageMetadata.Origin.REDIRECTED}) {
                    var excluded=hit(cause,origin,100);
                    HytaleDamageAdapter.GearResistanceFilter.applyResolved(excluded,defense,true,()->0);
                    assertEquals(100,excluded.getAmount());
                }
                var foreign=hit(cause,HytaleDamageMetadata.Origin.DIRECT,100);
                HytaleDamageAdapter.GearResistanceFilter.applyResolved(foreign,defense,false,()->0);
                assertEquals(100,foreign.getAmount(),"summon guard owns NPC resistance");
            }
            var physical=hit("Physical",HytaleDamageMetadata.Origin.DIRECT,100);
            HytaleDamageAdapter.GearResistanceFilter.applyResolved(physical,defense,true,()->0);
            assertEquals(100,physical.getAmount());
        }
    }
}
