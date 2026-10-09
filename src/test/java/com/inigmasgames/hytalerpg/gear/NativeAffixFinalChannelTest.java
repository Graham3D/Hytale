package com.inigmasgames.hytalerpg.gear;

import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCalculatorSystems;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.inigmasgames.hytalerpg.combat.hytale.HytaleDamageAdapter;
import com.inigmasgames.hytalerpg.combat.hytale.HytaleDamageMetadata;
import com.inigmasgames.hytalerpg.difficulty.MonsterResistanceProfile;
import com.inigmasgames.hytalerpg.execution.hytale.HytaleDifficultyCombat;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Native accepted source and managed Damage carrier boundaries for local elemental flats. */
class NativeAffixFinalChannelTest {
    private static NativeAssetTestFixtures assets;
    @BeforeAll static void nativeAssets() throws Exception { assets=NativeAssetTestFixtures.open(); }
    @AfterAll static void closeAssets() { if(assets!=null)assets.close(); }

    private static GearInstance rolled(String id) {
        var catalog=GearCatalog.load();var definition=catalog.affix(id);
        var base=catalog.base("gm.sword_iron.nm");
        assertTrue(new GearBindings().require(base.id()).mapped(),id);
        assertTrue(GearDropGenerator.eligible(definition,base),id);
        var qa=new GearAffixQaSuite(catalog);
        var fixture=qa.fixtures().stream().filter(f->f.fixtureId().equals("ab-"+id.toLowerCase()+"-affixed"))
                .findFirst().orElseThrow();
        var authored=qa.preview(fixture).affixes().getFirst();
        var roll=new GearInstance.AffixRoll(id,definition.side(),definition.exclusionGroup(),
                authored.tier(),authored.value(),new GearRequirements.Gate(1,Map.of()),
                definition.name(),definition.name());
        return GearInstance.authoredQa(base,UUID.randomUUID(),base.sourceWindow().getLast(),1000,
                GearRarity.UNCOMMON,List.of(roll),BigDecimal.ZERO);
    }
    private static GearInstance plain(GearInstance item) {
        var base=GearCatalog.load().base(item.baseId());
        return GearInstance.authoredQa(base,UUID.randomUUID(),base.sourceWindow().getLast(),1000,
                GearRarity.UNCOMMON,List.of(),BigDecimal.ZERO);
    }
    private static GearEffectSnapshot admitted(GearInstance... items) {
        var candidates=new ArrayList<GearEquipmentResolution.Candidate>();
        for(var item:items)candidates.add(new GearEquipmentResolution.Candidate(item,true,true,true));
        var result=GearEquipmentResolution.resolve(99,MasterAffixTestEquipment.BASELINE,candidates,
                GearCatalog.load().affixes().stream().map(GearCatalog.Affix::id)
                        .collect(java.util.stream.Collectors.toUnmodifiableSet()));
        assertEquals(List.of(items),result.validItems(),result.rejected().toString());
        return result.effects().snapshot();
    }
    private static Damage nativeDamage(String cause,boolean witness) {
        var damage=new Damage(Damage.NULL_SOURCE,DamageCause.getAssetMap().getAsset(cause),10);
        damage.putMetaObject(Damage.INTERACTION_TYPE,InteractionType.Primary);
        if(witness)damage.putMetaObject(DamageCalculatorSystems.DAMAGE_SEQUENCE,
                new DamageCalculatorSystems.DamageSequence(new DamageCalculatorSystems.Sequence(),
                        new ManagedGearDamageInteraction.Calculator()));
        return damage;
    }

    @Test void wa017Through022WrongActiveSourceAndUnsupportedCarrierSubmitNoElementalChannel() {
        var channels=List.of(GearCombatEffects.Channel.WIND,GearCombatEffects.Channel.WATER,
                GearCombatEffects.Channel.FIRE,GearCombatEffects.Channel.EARTH,
                GearCombatEffects.Channel.LIGHTNING,GearCombatEffects.Channel.VOID);
        for(int i=0;i<channels.size();i++) {
            String id="WA-"+String.format("%03d",17+i);
            var channel=channels.get(i);String cause=GearCombatEffects.nativeCause(channel);
            var item=rolled(id);var idle=plain(item);var snapshot=admitted(item,idle);
            var world=UUID.randomUUID();var actor=UUID.randomUUID();
            var wrong=new NativeGearAttackAcceptance.Chain(world,actor,idle.identity(),snapshot,0,1.5,"primary");
            var rolledHit=GearCombatEffects.attack(snapshot,item.identity(),id+"/rolled",100,1,
                    true,false,0,null,0,1.5,false);
            var idleHit=GearCombatEffects.attack(snapshot,idle.identity(),id+"/idle",100,1,
                    true,false,0,null,0,1.5,false);
            assertTrue(rolledHit.amount(channel)>0,id);
            assertEquals(0,idleHit.amount(channel),1e-9,id);
            assertThrows(IllegalArgumentException.class,()->NativeGearAttackAcceptance.commit(wrong,
                    rolledHit,true,"primary",Map.of()),id);
            assertSame(idleHit,NativeGearAttackAcceptance.commit(wrong,idleHit,true,"primary",Map.of()),id);
            assertNull(NativeGearAttackAcceptance.find(world,actor,idleHit),id);
            assertEquals(0,ManagedGearDamageInteraction.pending(actor),id);
            try {
                var accepted=NativeGearAttackAcceptance.commit(world,actor,rolledHit,1,true,"primary",
                        new GearSignatureProcRuntime(()->.99));
                assertSame(accepted,NativeGearAttackAcceptance.find(world,actor,accepted).hit(),id);
                ManagedGearDamageInteraction.register(actor,accepted,Set.of(cause),InteractionType.Primary);
                assertNull(ManagedGearDamageInteraction.claim(nativeDamage("Fall",true),actor),id);
                assertEquals(1,ManagedGearDamageInteraction.pending(actor),id);
                assertSame(accepted,ManagedGearDamageInteraction.claim(nativeDamage(cause,true),actor),id);
                assertEquals(0,ManagedGearDamageInteraction.pending(actor),id);
                assertEquals(0,idleHit.amount(channel),1e-9,id);
            } finally {
                ManagedGearDamageInteraction.forget(actor);
                NativeGearAttackAcceptance.clearWorld(world);
            }
        }
    }

    private static Damage damage(String cause,GearCombatEffects.Hit hit,
                                 GearCombatEffects.Channel submitted,HytaleDamageMetadata.Origin origin) {
        var event=new Damage(Damage.NULL_SOURCE,DamageCause.getAssetMap().getAsset(cause),100);
        if(origin!=null)event.putMetaObject(HytaleDamageAdapter.RPG_METADATA,new com.google.gson.Gson().toJson(
                new HytaleDamageMetadata(UUID.randomUUID(),hit.rootId(),"primary","contact",100,100,
                        "primary",true,origin)));
        HytaleDamageAdapter.attachGearHit(event,new HytaleDamageAdapter.GearHitSource(hit,"contact",submitted));
        return event;
    }

    @Test void wa029Through034NativeDamagePenetratesOnlyMatchingDirectCauseWithoutChangingTarget() {
        var channels=List.of(GearCombatEffects.Channel.WIND,GearCombatEffects.Channel.WATER,
                GearCombatEffects.Channel.FIRE,GearCombatEffects.Channel.EARTH,
                GearCombatEffects.Channel.LIGHTNING,GearCombatEffects.Channel.VOID);
        var targets=List.of(MonsterResistanceProfile.Channel.WIND,MonsterResistanceProfile.Channel.COLD,
                MonsterResistanceProfile.Channel.FIRE,MonsterResistanceProfile.Channel.NATURE,
                MonsterResistanceProfile.Channel.LIGHTNING,MonsterResistanceProfile.Channel.VOID);
        for(int i=0;i<channels.size();i++) {
            String id="WA-"+String.format("%03d",29+i);var channel=channels.get(i);
            var targetChannel=targets.get(i);String cause=GearCombatEffects.nativeCause(channel);
            var item=rolled(id);var control=plain(item);
            var hit=GearCombatEffects.attack(admitted(item),item.identity(),id+"/root",100,1,
                    true,false,0,null,0,1.5,false);
            var unrolled=GearCombatEffects.attack(admitted(control),control.identity(),id+"/control",100,1,
                    true,false,0,null,0,1.5,false);
            var target=new MonsterResistanceProfile(Map.of(targetChannel,.5),Set.of());
            var originalResistance=target.resistance();var originalImmunities=target.immunities();
            var matching=damage(cause,hit,channel,HytaleDamageMetadata.Origin.DIRECT);
            var baseline=damage(cause,unrolled,channel,HytaleDamageMetadata.Origin.DIRECT);
            double piercing=HytaleDifficultyCombat.penetration(matching);
            assertTrue(piercing>0,id);
            assertEquals(0,HytaleDifficultyCombat.penetration(baseline),1e-9,id);
            assertEquals(100*(1-Math.max(0,.5-piercing)),
                    target.resolve(targetChannel,matching.getAmount(),piercing).amount(),1e-5,id);
            assertTrue(target.resolve(targetChannel,matching.getAmount(),piercing).amount()
                    >target.resolve(targetChannel,baseline.getAmount(),
                            HytaleDifficultyCombat.penetration(baseline)).amount(),id);
            String otherCause=GearCombatEffects.nativeCause(channels.get((i+1)%channels.size()));
            var wrong=damage(otherCause,hit,channel,HytaleDamageMetadata.Origin.DIRECT);
            var periodic=damage(cause,hit,channel,HytaleDamageMetadata.Origin.PERIODIC);
            var environment=damage(cause,hit,channel,null);
            assertEquals(0,HytaleDifficultyCombat.penetration(wrong),1e-9,id);
            assertEquals(100,target.resolve(HytaleDifficultyCombat.channel(otherCause),wrong.getAmount(),
                    HytaleDifficultyCombat.penetration(wrong)).amount(),1e-5,id);
            for(var excluded:List.of(periodic,environment)) {
                assertEquals(0,HytaleDifficultyCombat.penetration(excluded),1e-9,id);
                assertEquals(50,target.resolve(targetChannel,excluded.getAmount(),
                        HytaleDifficultyCombat.penetration(excluded)).amount(),1e-5,id);
            }
            var immune=new MonsterResistanceProfile(Map.of(targetChannel,.5),Set.of(targetChannel));
            assertEquals(0,immune.resolve(targetChannel,matching.getAmount(),piercing).amount(),1e-9,id);
            assertEquals("AUTHORED_ELEMENTAL_IMMUNITY",
                    immune.resolve(targetChannel,matching.getAmount(),piercing).reason(),id);
            assertEquals(originalResistance,target.resistance(),id);
            assertEquals(originalImmunities,target.immunities(),id);
            assertEquals(.5,target.effective(targetChannel),1e-9,id);
        }
    }
}
