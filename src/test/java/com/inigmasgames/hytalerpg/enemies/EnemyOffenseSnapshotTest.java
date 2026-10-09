package com.inigmasgames.hytalerpg.enemies;

import com.inigmasgames.hytalerpg.combat.damage.WeaponDamageExecution;
import java.util.*;
import org.junit.jupiter.api.Test;
import static com.inigmasgames.hytalerpg.enemies.EnemyAffixRegistry.Operator.*;
import static org.junit.jupiter.api.Assertions.*;

class EnemyOffenseSnapshotTest {
    final EnemyAffixSnapshotTest fixture=new EnemyAffixSnapshotTest();
    WeaponDamageExecution.Identity identity(String strike){return new WeaponDamageExecution.Identity(fixture.world,fixture.leader,"native-chain","native-action",strike);}
    @Test void completeSourceFreezesDifficultyRarityThenAddsElementAndTypedIncreasesOnce(){
        var descriptor=fixture.actor(false,List.of(fixture.affix(EXTRA_STRONG),fixture.affix(FIRE_ENCHANTED),fixture.affix(FRENZIED)),List.of(),null);
        var providers=EnemyAffixSnapshot.resolve(descriptor,fixture.balance,fixture.pack(),8000,true,true);
        var result=EnemyOffenseSnapshot.freeze(descriptor,identity("strike-1"),Map.of("Physical",100d),"native-melee",2,1,1,providers,0,"birth");
        assertEquals(220,result.sourcePower(),1e-10);assertEquals(220,result.sourceVector().get("Physical"),1e-10);
        assertEquals(385,result.affixedVector().get("Physical"),1e-10);assertEquals(71.5,result.affixedVector().get("Fire"),1e-10);
        assertEquals(220,result.sourcePower(),1e-10); // Native received damage/critical result is not an input.
        assertThrows(UnsupportedOperationException.class,()->result.affixedVector().put("Fire",1d));
    }
    @Test void spectralUsesOneStableStrikeSelectionWithoutVictimInputs(){
        var descriptor=fixture.actor(false,List.of(fixture.affix(SPECTRAL_HIT)),List.of(),null);
        var providers=EnemyAffixSnapshot.resolve(descriptor,fixture.balance,fixture.pack(),0,true,true);var seen=new HashSet<String>();
        for(int i=0;i<80;i++){
            var id=identity("strike-"+i);
            var first=EnemyOffenseSnapshot.freeze(descriptor,id,Map.of("Physical",100d),"melee",1,1,1,providers,0,"birth");
            var second=EnemyOffenseSnapshot.freeze(descriptor,id,Map.of("Physical",100d),"melee",1,1,1,providers,0,"birth");
            assertEquals(first,second);seen.add(first.spectralChannel());
            assertEquals(143,first.affixedVector().values().stream().mapToDouble(Double::doubleValue).sum(),1e-10);
        }
        assertEquals(6,seen.size());
    }
    @Test void sourceMappingIsProducerSpecificAndCursedNeverDebitsFrozenPoisonPower(){
        var descriptor=fixture.actor(false,List.of(fixture.affix(POISON_ENCHANTED)),List.of(),null);
        var providers=EnemyAffixSnapshot.resolve(descriptor,fixture.balance,fixture.pack(),0,true,true);
        var cursed=EnemyOffenseSnapshot.freeze(descriptor,identity("one"),Map.of("RPG_Nature",100d),"native-attack",1,1,1,providers,.15,"birth");
        assertEquals(110,cursed.sourcePower(),1e-10);assertEquals(93.5,cursed.affixedVector().get("RPG_Nature"),1e-10);
        assertThrows(IllegalArgumentException.class,()->EnemyOffenseSnapshot.freeze(descriptor,identity("one"),Map.of("RPG_Arcane",100d),"other-arcane",1,1,1,providers,0,"birth"));
        var approved=EnemyOffenseSnapshot.freeze(descriptor,identity("one"),Map.of("RPG_Arcane",100d),"arcane_bolt",1,1,1,providers,0,"birth");
        assertEquals(Set.of("Lightning"),approved.sourceVector().keySet());
    }
    @Test void scoutSupportedPhysicalAndKnockbackCardsKeepOneOriginalProjectilePacket(){
        var descriptor=fixture.actor(false,List.of(fixture.affix(EXTRA_STRONG),fixture.affix(KNOCKBACK)),List.of(),null);
        var providers=EnemyAffixSnapshot.resolve(descriptor,fixture.balance,fixture.pack(),0,false,false);
        var snapshot=EnemyOffenseSnapshot.freeze(descriptor,identity("arrow"),Map.of("Projectile",13d),
                "ActionAttack.execute",1,1,1,providers,0,"birth");
        assertEquals(Set.of("Projectile"),snapshot.sourceVector().keySet());
        assertEquals(Set.of("Projectile"),snapshot.affixedVector().keySet());
        assertEquals(snapshot.sourceVector().get("Projectile")*(1+providers.physicalIncrease()),
                snapshot.affixedVector().get("Projectile"),1e-10);
    }
}
