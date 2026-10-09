package com.inigmasgames.hytalerpg.enemies;

import com.google.gson.Gson;
import com.inigmasgames.hytalerpg.combat.damage.DamageChannels;
import com.inigmasgames.hytalerpg.difficulty.MonsterResistanceProfile;
import org.junit.jupiter.api.Test;
import java.util.*;
import static com.inigmasgames.hytalerpg.difficulty.MonsterResistanceProfile.Channel.*;
import static org.junit.jupiter.api.Assertions.*;

class EnemyCanonicalResistanceTest {
    @Test void approvedAliasesUseExactProducerEvidenceAndDoNotGuessOtherArcane(){
        var channels=DamageChannels.canonical();
        for(String cause:List.of("Poison","Nature","RPG_Nature"))assertEquals(DamageChannels.Channel.EARTH,channels.resolve(cause,null).channel());
        for(String cause:List.of("Shadow","Necrotic","RPG_Necrotic"))assertEquals(DamageChannels.Channel.VOID,channels.resolve(cause,null).channel());
        for(String producer:List.of("arcane_bolt","arcane_missiles")){
            assertEquals(DamageChannels.Channel.LIGHTNING,channels.resolve("RPG_Arcane",producer).channel());
            assertEquals("Lightning",channels.nativeCause("RPG_Arcane",producer));
        }
        for(String producer:Arrays.asList(null,"arcane","Arcane Bolt","summon_decoy","copied_arcane_bolt")){
            assertEquals(DamageChannels.State.UNRESOLVED_PRODUCER,channels.resolve("RPG_Arcane",producer).state());
            assertEquals("RPG_Arcane",channels.nativeCause("RPG_Arcane",producer));
        }
        assertEquals("Fire",channels.nativeCause("Fire","arcane_bolt"));
        assertEquals(DamageChannels.State.UNMAPPED_NATIVE_CAUSE,channels.resolve("Drowning","arcane_bolt").state());
    }
    @Test void ordinaryCapPrecedesEligiblePenetrationAndCannotBecomeImmunity(){
        var base=new MonsterResistanceProfile(Map.of(FIRE,.4),Set.of());
        var resistant=base.withProviders(Map.of(),Map.of(FIRE,.25),Set.of());
        assertEquals(45,resistant.resolve(FIRE,100,.1).amount(),1e-10);
        var overcap=new MonsterResistanceProfile(Map.of(FIRE,1.10),Set.of());
        assertEquals(1.10,overcap.raw(FIRE));assertEquals(.75,overcap.effective(FIRE));
        assertFalse(overcap.immune(FIRE));assertEquals(35,overcap.resolve(FIRE,100,.1).amount(),1e-10);
        assertEquals(25,overcap.resolve(FIRE,100,0).amount(),1e-10);
        assertEquals(0,overcap.withProviders(Map.of(),Map.of(),Set.of(FIRE)).resolve(FIRE,100,.75).amount());
        assertEquals(100,overcap.resolve(WIND,100).amount());
    }
    @Test void legacyKeysRemainChecksumStableWhileCanonicalConsumersShareFlagsAndFloors(){
        String saved="{\"resistance\":{\"COLD\":0.5,\"NATURE\":0.4,\"NECROTIC\":0.3},\"immunities\":[\"POISON\"]}";
        var gson=new Gson();var tree=com.google.gson.JsonParser.parseString(saved);
        var profile=gson.fromJson(tree,MonsterResistanceProfile.class);
        assertEquals(tree,gson.toJsonTree(profile));
        assertEquals(.5,profile.effective(WATER));assertEquals(.4,profile.effective(EARTH));assertEquals(.3,profile.effective(VOID));
        assertTrue(profile.immune(EARTH));assertEquals(0,profile.resolve(NATURE,100,.75).amount());
        var composed=profile.withProviders(Map.of(WATER,.6,EARTH,.2),Map.of(WATER,.2,VOID,.1),Set.of());
        assertEquals(.8,composed.raw(WATER),1e-10);assertEquals(.4,composed.raw(VOID),1e-10);assertTrue(composed.immune(EARTH));
        assertFalse(composed.resistance().containsKey(COLD));
        assertThrows(IllegalArgumentException.class,()->new MonsterResistanceProfile(Map.of(NATURE,.3,POISON,.4),Set.of()));
        assertEquals(.5,MonsterResistanceProfile.NONE.withProviders(Map.of(),Map.of(NATURE,.5,POISON,.5),Set.of()).raw(EARTH));
    }
}
