package com.inigmasgames.hytalerpg.gear;

import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.inigmasgames.hytalerpg.combat.hytale.*;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import com.inigmasgames.hytalerpg.execution.hytale.HytaleDifficultyCombat;
import com.inigmasgames.hytalerpg.difficulty.MonsterResistanceProfile;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Controlled legal QA carriers through the same outgoing/Gather/Filter helpers as native damage. */
class GearCombatProductionTest {
    private static com.inigmasgames.hytalerpg.gear.NativeAssetTestFixtures nativeAssets;
    @org.junit.jupiter.api.BeforeAll static void installNativeAssets() throws Exception {
        nativeAssets = com.inigmasgames.hytalerpg.gear.NativeAssetTestFixtures.open();
    }
    @org.junit.jupiter.api.AfterAll static void closeNativeAssets() {
        if (nativeAssets != null) nativeAssets.close();
    }

    private final GearCatalog catalog=GearCatalog.load();
    private final GearAffixQaSuite fixtures=new GearAffixQaSuite(catalog);
    private GearInstance item(String id,boolean control){
        id = String.format(Locale.ROOT, "WA-%03d", Integer.parseInt(id.substring(3)));
        String name="ab-"+id.toLowerCase(Locale.ROOT)+"-affixed";
        var fixture=fixtures.fixtures().stream().filter(f->f.fixtureId().equals(name)).findFirst().orElseThrow();
        var result=fixtures.preview(fixture);
        assertEquals(1,result.affixes().size());
        if(!control)return result;
        var base=catalog.base(result.baseId());
        return new GearInstance(result.schemaVersion(),UUID.randomUUID(),result.definitionRevision(),result.baseId(),
                result.baseName(),result.category(),result.sourceEra(),result.itemLevel(),GearRarity.COMMON,
                result.intrinsicThousandths(),result.intrinsicStats(),
                new GearRequirements.Gate(base.requiredLevel(),base.requiredAttributes()),List.of(),result.rngVersion(),true);
    }
    private GearCombatEffects.Hit attack(GearInstance item,double physical){
        return GearCombatEffects.attack(new GearEffectSnapshot(List.of(item)),item.identity(),"root-1",physical,1,
                true,false,0,null,0,1.5,false,new Vec3(0,0,0));
    }
    private GearInstance sword(String affixId){
        var base=catalog.base("gm.sword_adamantite.h");
        List<GearInstance.AffixRoll> rolls=List.of();
        if(affixId!=null){
            var affix=catalog.affix(affixId);
            assertTrue(GearDropGenerator.eligible(affix,base));
            var tier=GearAffixTiers.compile(affix).getFirst();
            rolls=List.of(new GearInstance.AffixRoll(affixId,affix.side(),affix.exclusionGroup(),tier.tier(),
                    tier.low(),new GearRequirements.Gate(tier.requiredLevel(),Map.of()),affix.name(),affix.name()));
        }
        return GearInstance.authoredQa(base,UUID.randomUUID(),base.sourceWindow().getLast(),1000,
                affixId==null?GearRarity.COMMON:GearRarity.MAGIC,rolls,java.math.BigDecimal.ZERO);
    }
    @Test void quickSlashMixedWeaponComponentsReachNativeGearChannelsOnceWithSourceFireWitness(){
        var source=sword("WA-019");
        var idle=new GearInstance(source.schemaVersion(),UUID.randomUUID(),source.definitionRevision(),source.baseId(),
                source.baseName(),source.category(),source.sourceEra(),source.itemLevel(),source.rarity(),
                source.intrinsicThousandths(),source.intrinsicStats(),source.requirements(),List.of(),source.rngVersion(),true);
        var equipped=new GearEffectSnapshot(List.of(source,idle));
        var profile=new com.inigmasgames.hytalerpg.combat.power.WeaponLightAttackProfile("sword","SWORD","fixture",.4,.12,List.of(
                new com.inigmasgames.hytalerpg.combat.power.WeaponLightAttackProfile.Component("physical","PHYSICAL",100,100,"shared",
                        com.inigmasgames.hytalerpg.combat.damage.WeaponDamageExecution.Provenance.WEAPON,true),
                new com.inigmasgames.hytalerpg.combat.power.WeaponLightAttackProfile.Component("fire","FIRE",20,20,"shared",
                        com.inigmasgames.hytalerpg.combat.damage.WeaponDamageExecution.Provenance.FLAME_WEAPON,true)));
        var light=com.inigmasgames.hytalerpg.execution.strike.WeaponLightHit.create(profile,.375,"slash",()->.5,false,true);
        var identity=new com.inigmasgames.hytalerpg.combat.damage.WeaponDamageExecution.Identity(UUID.randomUUID(),UUID.randomUUID(),"root","slash","slash");
        var decision=new com.inigmasgames.hytalerpg.combat.damage.WeaponFireDecision(light.envelope(identity,calculator(1),0,
                com.inigmasgames.hytalerpg.combat.damage.ModifierBuckets.NONE,1.5,false));
        assertEquals(7.5,decision.execution().sourceFire(),1e-9);
        var physical=light.sampled().getFirst();var fire=light.sampled().getLast();
        var buckets=com.inigmasgames.hytalerpg.combat.damage.ModifierBuckets.NONE;
        var first=GearCombatEffects.skill(equipped,source.identity(),"root",GearCombatEffects.Channel.PHYSICAL,
                light.calculate(calculator(1),physical,0,buckets,1.5),buckets,light.multiplier(),true,false,0,null,1.5,Vec3.ZERO,true);
        var second=GearCombatEffects.skill(equipped,source.identity(),"root",GearCombatEffects.Channel.FIRE,
                light.calculate(calculator(1),fire,0,buckets,1.5),buckets,light.multiplier(),true,false,0,null,1.5,Vec3.ZERO,false);
        assertEquals(37.5,first.amount(GearCombatEffects.Channel.PHYSICAL),1e-9);
        assertEquals(source.affixes().getFirst().value()*.375,first.amount(GearCombatEffects.Channel.FIRE),1e-9);
        assertEquals(7.5,second.amount(GearCombatEffects.Channel.FIRE),1e-9);
        var contact="cast/slash/0/victim";
        var physicalMeta=new HytaleDamageMetadata(identity.actorId(),"root","slash",contact,
                first.amount(GearCombatEffects.Channel.PHYSICAL),Double.NaN,"slash",true,HytaleDamageMetadata.Origin.DIRECT);
        var firstMeta=new HytaleDamageMetadata(identity.actorId(),"root","slash",contact,
                first.amount(GearCombatEffects.Channel.FIRE),Double.NaN,"slash",false,HytaleDamageMetadata.Origin.DIRECT);
        var secondMeta=new HytaleDamageMetadata(identity.actorId(),"root","slash",contact,
                second.amount(GearCombatEffects.Channel.FIRE),Double.NaN,"slash",false,HytaleDamageMetadata.Origin.DIRECT);
        var fireCause=DamageCause.getAssetMap().getAsset("Fire");
        var physicalEvent=HytaleDamageAdapter.prepareGearDamage(null,DamageCause.getAssetMap().getAsset("Physical"),
                physicalMeta,first,GearCombatEffects.Channel.PHYSICAL,null,null,
                new HytaleDamageAdapter.WeaponComponent(decision,physical.componentId()));
        var added=HytaleDamageAdapter.prepareGearDamage(null,fireCause,firstMeta,first,GearCombatEffects.Channel.FIRE,null,null);
        var authored=HytaleDamageAdapter.prepareGearDamage(null,fireCause,secondMeta,second,GearCombatEffects.Channel.FIRE,null,null,
                new HytaleDamageAdapter.WeaponComponent(decision,fire.componentId()));
        assertNull(HytaleDamageAdapter.weaponComponent(added));
        assertTrue(HytaleDamageAdapter.metadata(physicalEvent).canProc());
        assertFalse(HytaleDamageAdapter.metadata(added).canProc());
        assertSame(decision,HytaleDamageAdapter.weaponComponent(authored).decision());
        assertFalse(HytaleDamageAdapter.metadata(authored).canProc());
        assertEquals(contact,HytaleDamageAdapter.gearHit(authored).contactId());
        var wrong=new HytaleDamageAdapter.WeaponComponent(decision,physical.componentId());
        assertThrows(IllegalArgumentException.class,()->HytaleDamageAdapter.prepareGearDamage(null,fireCause,
                secondMeta,second,GearCombatEffects.Channel.FIRE,null,null,wrong));
        var noLocal=GearCombatEffects.skill(equipped,idle.identity(),"root",GearCombatEffects.Channel.PHYSICAL,
                light.calculate(calculator(1),physical,0,buckets,1.5),buckets,light.multiplier(),true,false,0,null,1.5,Vec3.ZERO,true);
        assertEquals(0,noLocal.amount(GearCombatEffects.Channel.FIRE));
        var control=sword(null);
        var noAffix=GearCombatEffects.skill(new GearEffectSnapshot(List.of(control)),control.identity(),"root",
                GearCombatEffects.Channel.PHYSICAL,light.calculate(calculator(1),physical,0,buckets,1.5),buckets,
                light.multiplier(),true,false,0,null,1.5,Vec3.ZERO,true);
        assertEquals(0,noAffix.amount(GearCombatEffects.Channel.FIRE));
    }
    private com.inigmasgames.hytalerpg.combat.damage.DamageCalculationService calculator(double draw){
        return new com.inigmasgames.hytalerpg.combat.damage.DamageCalculationService(
                new com.inigmasgames.hytalerpg.combat.damage.SkillScalingService(
                        com.inigmasgames.hytalerpg.combat.balance.CombatBalanceProfile.loadCanonical()),
                new com.inigmasgames.hytalerpg.combat.damage.CriticalRoller(()->draw));
    }
    @Test void directSkillProductionCompositionKeepsNativeScalingAndOneCriticalRoll(){
        var buckets=new com.inigmasgames.hytalerpg.combat.damage.ModifierBuckets(List.of(.20),List.of(),List.of(1.5),List.of());
        var result=calculator(0).calculate(new com.inigmasgames.hytalerpg.combat.damage.DamageCalculationService.Request(
                100,0,1,buckets,true,1,1.5));
        var spell=item("WA-004",false);var spellControl=item("WA-004",true);
        var with=GearCombatEffects.skill(new GearEffectSnapshot(List.of(spell)),spell.identity(),"skill-root",
                GearCombatEffects.Channel.FIRE,result,buckets,1,false,true,0,null,1.5,Vec3.ZERO);
        var without=GearCombatEffects.skill(new GearEffectSnapshot(List.of(spellControl)),spellControl.identity(),"skill-root",
                GearCombatEffects.Channel.FIRE,result,buckets,1,false,true,0,null,1.5,Vec3.ZERO);
        assertTrue(with.critical());
        assertEquals(without.amount(GearCombatEffects.Channel.FIRE)+
                100*spell.affixes().getFirst().value()/100*1.5*1.5,
                with.amount(GearCombatEffects.Channel.FIRE),1e-7);
        assertEquals(0,with.amount(GearCombatEffects.Channel.PHYSICAL));
        var fireFlat=item("WA-019",false);var flatControl=item("WA-019",true);
        var flat=GearCombatEffects.skill(new GearEffectSnapshot(List.of(fireFlat)),fireFlat.identity(),"flat-root",
                GearCombatEffects.Channel.PHYSICAL,result,buckets,1,true,false,0,null,1.5,Vec3.ZERO);
        var baseline=GearCombatEffects.skill(new GearEffectSnapshot(List.of(flatControl)),flatControl.identity(),"flat-root",
                GearCombatEffects.Channel.PHYSICAL,result,buckets,1,true,false,0,null,1.5,Vec3.ZERO);
        assertEquals(fireFlat.affixes().getFirst().value()*buckets.factor()*1.5,
                flat.amount(GearCombatEffects.Channel.FIRE)-baseline.amount(GearCombatEffects.Channel.FIRE),1e-7);
        var metadata=new HytaleDamageMetadata(UUID.randomUUID(),flat.rootId(),"skill","skill-contact",
                flat.amount(GearCombatEffects.Channel.FIRE),Double.NaN,"skill-contact",true,
                HytaleDamageMetadata.Origin.DIRECT);
        var nativeFire=DamageCause.getAssetMap().getAsset("Fire");
        var nativeEvent=HytaleDamageAdapter.prepareGearDamage(null,nativeFire,metadata,flat,
                GearCombatEffects.Channel.FIRE,null,null);
        assertEquals(flat.amount(GearCombatEffects.Channel.FIRE),nativeEvent.getAmount(),1e-4);
        assertEquals(fireFlat.identity(),HytaleDamageAdapter.gearHit(nativeEvent).hit().itemId());
        assertEquals("skill-contact",HytaleDamageAdapter.gearHit(nativeEvent).contactId());
        assertThrows(IllegalArgumentException.class,()->HytaleDamageAdapter.prepareGearDamage(null,nativeFire,metadata,
                flat,GearCombatEffects.Channel.WATER,null,null));
        var foreign=new HytaleDamageMetadata(metadata.actorId(),"foreign",metadata.skillInstanceId(),metadata.correlationId(),
                metadata.preMitigationDamage(),Double.NaN,metadata.effectInstanceId(),true,metadata.origin());
        assertThrows(IllegalArgumentException.class,()->HytaleDamageAdapter.prepareGearDamage(null,nativeFire,foreign,
                flat,GearCombatEffects.Channel.FIRE,null,null));
        var binding=new GearBindings().require(fireFlat.baseId());
        assertEquals(fireFlat.identity(),GearCombatEffects.contributingItem(new GearEffectSnapshot(List.of(fireFlat)),
                binding.carrier(fireFlat.rarity())));
        assertNull(GearCombatEffects.contributingItem(new GearEffectSnapshot(List.of(fireFlat)),"unmanaged-item"));
        var duplicate=new GearInstance(fireFlat.schemaVersion(),UUID.randomUUID(),fireFlat.definitionRevision(),fireFlat.baseId(),
                fireFlat.baseName(),fireFlat.category(),fireFlat.sourceEra(),fireFlat.itemLevel(),fireFlat.rarity(),
                fireFlat.intrinsicThousandths(),fireFlat.intrinsicStats(),fireFlat.requirements(),fireFlat.affixes(),
                fireFlat.rngVersion(),true);
        assertThrows(IllegalStateException.class,()->GearCombatEffects.contributingItem(
                new GearEffectSnapshot(List.of(fireFlat,duplicate)),binding.carrier(fireFlat.rarity())));
    }
    @Test void skillConditionalGatherAddsToExistingSkillAndGearIncreasedBucket(){
        var item=item("WA-041",false);
        var buckets=new com.inigmasgames.hytalerpg.combat.damage.ModifierBuckets(List.of(.20),List.of(),List.of(1.5),List.of());
        var result=calculator(1).calculate(new com.inigmasgames.hytalerpg.combat.damage.DamageCalculationService.Request(
                100,0,1,buckets,true,0,1.5));
        var hit=GearCombatEffects.skill(new GearEffectSnapshot(List.of(item)),item.identity(),"skill-conditional",
                GearCombatEffects.Channel.PHYSICAL,result,buckets,1,true,false,0,null,1.5,Vec3.ZERO);
        var meta=new HytaleDamageMetadata(UUID.randomUUID(),hit.rootId(),"skill","contact",hit.amount(GearCombatEffects.Channel.PHYSICAL),
                100,"skill",true,HytaleDamageMetadata.Origin.DIRECT);
        var event=HytaleDamageAdapter.prepareGearDamage(null,DamageCause.PHYSICAL,meta,hit,
                GearCombatEffects.Channel.PHYSICAL,null,null);
        double bonus=item.affixes().getFirst().value()/100;
        assertEquals(bonus,HytaleConditionalDamage.gearGather(event,new GearHitConditions.Context(
                Vec3.ZERO,new Vec3(0,0,3),Vec3.FORWARD,100,100,Set.of(),false)),1e-9);
        assertEquals(100*(1+.20+bonus)*1.5,event.getAmount(),1e-4);
        assertEquals(0,HytaleConditionalDamage.gearGather(event,new GearHitConditions.Context(
                Vec3.ZERO,new Vec3(0,0,3),Vec3.FORWARD,100,100,Set.of(),false)));
        var far=HytaleDamageAdapter.prepareGearDamage(null,DamageCause.PHYSICAL,meta,hit,
                GearCombatEffects.Channel.PHYSICAL,null,null);
        assertEquals(0,HytaleConditionalDamage.gearGather(far,new GearHitConditions.Context(
                Vec3.ZERO,new Vec3(0,0,5),Vec3.FORWARD,100,100,Set.of(),false)));
        assertEquals(hit.amount(GearCombatEffects.Channel.PHYSICAL),far.getAmount(),1e-4);
    }
    @Test void nativeNpcResistanceReadsOnlyMatchingDirectGearPenetrationOnce(){
        var item=item("WA-031",false); // Fire penetration on a legal carrier.
        var hit=attack(item,100);
        var fire=DamageCause.getAssetMap().getAsset("Fire");
        var damage=new Damage(Damage.NULL_SOURCE,fire,100);
        damage.putMetaObject(HytaleDamageAdapter.RPG_METADATA,new com.google.gson.Gson().toJson(
                new HytaleDamageMetadata(UUID.randomUUID(),hit.rootId(),"skill","contact",100,100,
                        "skill",true,HytaleDamageMetadata.Origin.DIRECT)));
        HytaleDamageAdapter.attachGearHit(damage,new HytaleDamageAdapter.GearHitSource(hit,"contact",GearCombatEffects.Channel.FIRE));
        double penetration=hit.penetration(GearCombatEffects.Channel.FIRE);
        assertEquals(penetration,HytaleDifficultyCombat.penetration(damage),1e-9);
        var resistance=new MonsterResistanceProfile(Map.of(MonsterResistanceProfile.Channel.FIRE,.5),Set.of());
        assertEquals(100*(1-Math.max(0,.5-penetration)),
                resistance.resolve(MonsterResistanceProfile.Channel.FIRE,damage.getAmount(),HytaleDifficultyCombat.penetration(damage)).amount(),1e-6);
        assertEquals(50,resistance.resolve(MonsterResistanceProfile.Channel.FIRE,100).amount());
        assertEquals(0,new MonsterResistanceProfile(Map.of(MonsterResistanceProfile.Channel.FIRE,.5),
                Set.of(MonsterResistanceProfile.Channel.FIRE)).resolve(MonsterResistanceProfile.Channel.FIRE,100,penetration).amount());
        var wrong=new Damage(Damage.NULL_SOURCE,DamageCause.getAssetMap().getAsset("Ice"),100);
        wrong.putMetaObject(HytaleDamageAdapter.RPG_METADATA,damage.getIfPresentMetaObject(HytaleDamageAdapter.RPG_METADATA));
        HytaleDamageAdapter.attachGearHit(wrong,new HytaleDamageAdapter.GearHitSource(hit,"contact",GearCombatEffects.Channel.FIRE));
        assertEquals(0,HytaleDifficultyCombat.penetration(wrong));
        var periodic=new Damage(Damage.NULL_SOURCE,fire,100);
        periodic.putMetaObject(HytaleDamageAdapter.RPG_METADATA,new com.google.gson.Gson().toJson(
                new HytaleDamageMetadata(UUID.randomUUID(),hit.rootId(),"skill","contact",100,100,
                        "skill",false,HytaleDamageMetadata.Origin.PERIODIC)));
        HytaleDamageAdapter.attachGearHit(periodic,new HytaleDamageAdapter.GearHitSource(hit,"contact",GearCombatEffects.Channel.FIRE));
        assertEquals(0,HytaleDifficultyCombat.penetration(periodic));
    }
    @Test void playerNativeResistanceAndGearShareOneCapAndOnePenetration(){
        assertEquals(.875,HytaleDamageAdapter.combinedResistanceFactor(.20,.20,.10),1e-9);
        assertEquals(70,80*HytaleDamageAdapter.combinedResistanceFactor(.20,.20,.10),1e-9);
        assertEquals(25,50*HytaleDamageAdapter.combinedResistanceFactor(.50,.50,0),1e-9);
        assertEquals(100,80*HytaleDamageAdapter.combinedResistanceFactor(.20,0,.20),1e-9);
        assertEquals(80,80*HytaleDamageAdapter.combinedResistanceFactor(.20,0,0),1e-9);
        assertThrows(IllegalArgumentException.class,()->HytaleDamageAdapter.combinedResistanceFactor(1,.1,0));
    }
    @Test void defenseBreakRecomputesFromLiveFractionWithoutChangingFrozenRating(){
        var view=GearDefenseEffects.resolve(GearEffectSnapshot.EMPTY,50,.40,0,0);
        assertEquals(view.managedProtection(),GearDefenseEffects.reduction(view,50,0),1e-9);
        assertTrue(GearDefenseEffects.reduction(view,50,.50)<view.managedProtection());
        assertEquals(0,GearDefenseEffects.reduction(view,50,1));
        assertThrows(IllegalArgumentException.class,()->GearDefenseEffects.reduction(view,50,1.01));
    }
    @Test void nativeMeleeAndProjectilePowerUsesTheGoldenRangeAndPreservesTheCoefficient(){
        var range=GearCombatEffects.physicalRange(12,20,8,5,12,60);
        assertEquals(40,range.minimum());assertEquals(64,range.maximum());
        assertEquals(57.2,52*1.10,1e-9);
        var fire=item("WA-019",false);var control=item("WA-019",true);
        double flat=fire.affixes().getFirst().value();
        var with=GearCombatEffects.attack(new GearEffectSnapshot(List.of(fire)),fire.identity(),"same-strike",52,1.10,
                true,false,0,null,0,1.5,false);
        var without=GearCombatEffects.attack(new GearEffectSnapshot(List.of(control)),control.identity(),"same-strike",52,1.10,
                true,false,0,null,0,1.5,false);
        assertEquals(57.2,with.amount(GearCombatEffects.Channel.PHYSICAL),1e-9);
        assertEquals(flat*1.10,with.amount(GearCombatEffects.Channel.FIRE),1e-9);
        assertEquals(0,without.amount(GearCombatEffects.Channel.FIRE));
        assertThrows(IllegalArgumentException.class,()->GearCombatEffects.attack(
                new GearEffectSnapshot(List.of(control)),fire.identity(),"invalid-source",52,1.10,
                true,false,0,null,0,1.5,false));
        assertEquals("Projectile",GearCombatEffects.nativeChannel("Projectile")==GearCombatEffects.Channel.PHYSICAL?"Projectile":"wrong");
        var honed=item("WA-001",false);var honedControl=item("WA-001",true);
        assertEquals(honed.affixes().getFirst().value(),
                GearCombatEffects.physical(honed).minimum()-GearCombatEffects.physical(honedControl).minimum(),1e-9);
        var brutal=item("WA-002",false);var brutalControl=item("WA-002",true);
        assertTrue(GearCombatEffects.physical(brutal).minimum()>GearCombatEffects.physical(brutalControl).minimum());
        assertFalse(GearCombatEffects.needsNativeEnvelope(attack(honed,100)));
        assertFalse(GearCombatEffects.needsNativeEnvelope(GearCombatEffects.attack(
                new GearEffectSnapshot(List.of(honed,item("WA-005",false))),honed.identity(),"idle-offhand",100,1,
                true,false,0,null,0,1.5,false)));
        assertTrue(GearCombatEffects.needsNativeEnvelope(attack(fire,100)));
        assertEquals(ManagedGearDamageInteraction.samplePower(brutal,"same-native-root"),
                ManagedGearProjectile.samplePower(brutal,"same-native-root"));
        double projectile=ManagedGearProjectile.samplePower(brutal,"frozen-launch");
        assertEquals(projectile,ManagedGearProjectile.samplePower(brutal,"frozen-launch"));
    }
    @Test void sixFlatIncreasedConversionAndPenetrationFamiliesUseTheActualChannelConsumer(){
        var channels=List.of(GearCombatEffects.Channel.WIND,GearCombatEffects.Channel.WATER,
                GearCombatEffects.Channel.FIRE,GearCombatEffects.Channel.EARTH,
                GearCombatEffects.Channel.LIGHTNING,GearCombatEffects.Channel.VOID);
        for(int i=0;i<6;i++){
            var channel=channels.get(i);
            assertEquals(channel,GearCombatEffects.nativeChannel(GearCombatEffects.nativeCause(channel)));
            var flat=item("WA-"+(17+i),false);var baseline=item("WA-"+(17+i),true);
            assertEquals(flat.affixes().getFirst().value(),attack(flat,100).amount(channel),1e-9);
            assertEquals(0,attack(baseline,100).amount(channel));
            var increased=item("WA-"+(23+i),false);baseline=item("WA-"+(23+i),true);
            // A second valid item contributes global elemental increase to the actual source item's flat channel.
            assertEquals(100,attack(increased,100).amount(GearCombatEffects.Channel.PHYSICAL));
            assertEquals(0,attack(increased,100).amount(channel));
            assertEquals(0,attack(baseline,100).amount(channel));
            var buffed=GearCombatEffects.attack(new GearEffectSnapshot(List.of(flat,increased)),flat.identity(),"shared",100,1,
                    true,false,0,null,0,1.5,false);
            var unbuffed=GearCombatEffects.attack(new GearEffectSnapshot(List.of(flat,baseline)),flat.identity(),"shared",100,1,
                    true,false,0,null,0,1.5,false);
            assertEquals(unbuffed.amount(channel)*(1+increased.affixes().getFirst().value()/100),buffed.amount(channel),1e-9);
            var converted=item("WA-"+(35+i),false);baseline=item("WA-"+(35+i),true);
            double fraction=converted.affixes().getFirst().value()/100;
            var moved=attack(converted,100);
            assertEquals(100*fraction,moved.amount(channel),1e-9);
            assertEquals(100*(1-fraction),moved.amount(GearCombatEffects.Channel.PHYSICAL),1e-9);
            assertEquals(100,attack(baseline,100).amount(GearCombatEffects.Channel.PHYSICAL));
            var afterSkill=GearCombatEffects.attack(new GearEffectSnapshot(List.of(converted)),converted.identity(),"skill-first",100,1,
                    true,false,.8,GearCombatEffects.Channel.FIRE,0,1.5,false);
            assertEquals(100*Math.max(0,.2-fraction),afterSkill.amount(GearCombatEffects.Channel.PHYSICAL),1e-9);
            assertEquals(100,afterSkill.amounts().values().stream().mapToDouble(Double::doubleValue).sum(),1e-9);
            var piercing=item("WA-"+(29+i),false);baseline=item("WA-"+(29+i),true);
            assertEquals(piercing.affixes().getFirst().value()/100,attack(piercing,100).penetration(channel));
            assertEquals(0,attack(baseline,100).penetration(channel));
            assertTrue(GearCombatEffects.resisted(100,GearEffectSnapshot.EMPTY,channel,.4,attack(piercing,100).penetration(channel))
                    >GearCombatEffects.resisted(100,GearEffectSnapshot.EMPTY,channel,.4,attack(baseline,100).penetration(channel)));
            assertEquals(100,GearCombatEffects.resisted(100,new GearEffectSnapshot(List.of(baseline)),
                    GearCombatEffects.Channel.PHYSICAL,0,.5));
        }
    }
    @Test void attackSpellPhysicalAndElementBucketsRespectSourceAndActionTags(){
        var spell=item("WA-004",false);var spellControl=item("WA-004",true);
        double spellBonus=spell.affixes().getFirst().value()/100;
        var spells=new GearEffectSnapshot(List.of(spell));
        assertEquals(100*(1+spellBonus),GearCombatEffects.attack(spells,spell.identity(),"spell",100,1,
                false,true,0,null,0,1.5,false).amount(GearCombatEffects.Channel.PHYSICAL),1e-9);
        assertEquals(100,GearCombatEffects.attack(spells,spell.identity(),"attack",100,1,
                false,false,0,null,0,1.5,false).amount(GearCombatEffects.Channel.PHYSICAL));
        assertEquals(100,attack(spellControl,100).amount(GearCombatEffects.Channel.PHYSICAL));
        assertEquals(100,GearCombatEffects.attack(GearEffectSnapshot.EMPTY,null,"unarmed-spell",100,1,
                false,true,0,null,0,1.5,false).amount(GearCombatEffects.Channel.PHYSICAL));
        var soldier=item("WA-005",false);var soldierControl=item("WA-005",true);
        assertEquals(100*(1+soldier.affixes().getFirst().value()/100),attack(soldier,100).amount(GearCombatEffects.Channel.PHYSICAL),1e-9);
        assertEquals(100,attack(soldierControl,100).amount(GearCombatEffects.Channel.PHYSICAL));
        assertEquals(100,GearCombatEffects.attack(new GearEffectSnapshot(List.of(soldier)),soldier.identity(),"not-attack",100,1,
                false,false,0,null,0,1.5,false).amount(GearCombatEffects.Channel.PHYSICAL));
        var physical=item("WA-006",false);var physicalControl=item("WA-006",true);
        assertEquals(100*(1+physical.affixes().getFirst().value()/100),attack(physical,100).amount(GearCombatEffects.Channel.PHYSICAL),1e-9);
        assertEquals(100,attack(physicalControl,100).amount(GearCombatEffects.Channel.PHYSICAL));
        var prism=item("WA-007",false);var prismControl=item("WA-007",true);var flat=item("WA-019",false);
        var applied=GearCombatEffects.attack(new GearEffectSnapshot(List.of(flat,prism)),flat.identity(),"element",100,1,
                true,false,0,null,0,1.5,false);
        var absent=GearCombatEffects.attack(new GearEffectSnapshot(List.of(flat,prismControl)),flat.identity(),"element",100,1,
                true,false,0,null,0,1.5,false);
        assertEquals(absent.amount(GearCombatEffects.Channel.FIRE)*(1+prism.affixes().getFirst().value()/100),
                applied.amount(GearCombatEffects.Channel.FIRE),1e-9);
        assertEquals(absent.amount(GearCombatEffects.Channel.PHYSICAL),applied.amount(GearCombatEffects.Channel.PHYSICAL));
    }
    @Test void criticalSourcesUseOneRootOutcomeAndOnlyIncreaseCriticalMultiplier(){
        var precision=item("WA-010",false);var control=item("WA-010",true);
        String winning=null;
        for(int i=0;i<10000;i++){
            String root="crit/"+i;
            boolean buff=GearCombatEffects.attack(new GearEffectSnapshot(List.of(precision)),precision.identity(),root,100,1,
                    true,false,0,null,0,1.5,true).critical();
            boolean plain=GearCombatEffects.attack(new GearEffectSnapshot(List.of(control)),control.identity(),root,100,1,
                    true,false,0,null,0,1.5,true).critical();
            if(buff&&!plain){winning=root;break;}
        }
        assertNotNull(winning);
        var once=GearCombatEffects.attack(new GearEffectSnapshot(List.of(precision)),precision.identity(),winning,100,1,
                true,false,0,null,0,1.5,true);
        assertTrue(once.critical());
        assertEquals(once, GearCombatEffects.attack(new GearEffectSnapshot(List.of(precision)),precision.identity(),winning,100,1,
                true,false,0,null,0,1.5,true));
        var lethal=item("WA-011",false);var lethalControl=item("WA-011",true);
        String root=null;
        for(int i=0;i<100;i++)if(GearCombatEffects.attack(new GearEffectSnapshot(List.of(lethal)),lethal.identity(),"mult/"+i,100,1,
                true,false,0,null,.75,1.5,true).critical()){root="mult/"+i;break;}
        assertNotNull(root);
        var with=GearCombatEffects.attack(new GearEffectSnapshot(List.of(lethal)),lethal.identity(),root,100,1,
                true,false,0,null,.75,1.5,true);
        var without=GearCombatEffects.attack(new GearEffectSnapshot(List.of(lethalControl)),lethalControl.identity(),root,100,1,
                true,false,0,null,.75,1.5,true);
        assertEquals(150+lethal.affixes().getFirst().value(),with.amount(GearCombatEffects.Channel.PHYSICAL),1e-9);
        assertEquals(150,without.amount(GearCombatEffects.Channel.PHYSICAL),1e-9);
        assertEquals(100,GearCombatEffects.attack(new GearEffectSnapshot(List.of(lethal)),lethal.identity(),root,100,1,
                true,false,0,null,.75,1.5,false).amount(GearCombatEffects.Channel.PHYSICAL));
        var kernel=new com.inigmasgames.hytalerpg.combat.damage.DamageCalculationService(
                new com.inigmasgames.hytalerpg.combat.damage.SkillScalingService(
                        com.inigmasgames.hytalerpg.combat.balance.CombatBalanceProfile.loadCanonical()),
                new com.inigmasgames.hytalerpg.combat.damage.CriticalRoller(()->0.0));
        assertTrue(kernel.calculateGear(new GearEffectSnapshot(List.of(precision)),precision.identity(),"skill-root",100,1,
                true,false,0,null,0,1.5,true,Vec3.ZERO).critical());
        assertFalse(kernel.calculateGear(new GearEffectSnapshot(List.of(control)),control.identity(),"skill-control",100,1,
                true,false,0,null,0,1.5,true,Vec3.ZERO).critical());
    }
    @Test void resistanceIsLiveCappedAndNeverAppliesToHazardsOrOtherChannels(){
        var channels=List.of(GearCombatEffects.Channel.WIND,GearCombatEffects.Channel.WATER,
                GearCombatEffects.Channel.FIRE,GearCombatEffects.Channel.EARTH,
                GearCombatEffects.Channel.LIGHTNING,GearCombatEffects.Channel.VOID);
        for(int i=0;i<6;i++){
            var source=item("WA-"+(72+i),false);var untreated=item("WA-"+(72+i),true);
            var channel=channels.get(i);
            assertEquals(100*(1-source.affixes().getFirst().value()/100),
                    GearCombatEffects.resisted(100,new GearEffectSnapshot(List.of(source)),channel,0,0),1e-9);
            assertEquals(100,GearCombatEffects.resisted(100,new GearEffectSnapshot(List.of(untreated)),channel,0,0));
            var other=channels.get((i+1)%6);
            assertEquals(100,GearCombatEffects.resisted(100,new GearEffectSnapshot(List.of(source)),other,0,0));
        }
        var fire=item("WA-074",false);var control=item("WA-074",true);
        var defense=new GearEffectSnapshot(List.of(fire));var empty=new GearEffectSnapshot(List.of(control));
        double added=fire.affixes().getFirst().value()/100;
        assertEquals(100*(1-added),GearCombatEffects.resisted(100,defense,GearCombatEffects.Channel.FIRE,0,0),1e-9);
        assertEquals(100,GearCombatEffects.resisted(100,empty,GearCombatEffects.Channel.FIRE,0,0));
        assertEquals(100,GearCombatEffects.resisted(100,defense,GearCombatEffects.Channel.WATER,0,0));
        assertEquals(25,GearCombatEffects.resisted(100,defense,GearCombatEffects.Channel.FIRE,.9,0));
        assertEquals(60,GearCombatEffects.resisted(100,defense,GearCombatEffects.Channel.FIRE,.9,.35));
        assertNull(GearCombatEffects.nativeChannel("Lava"));
        var nativeFire=new Damage(Damage.NULL_SOURCE,new DamageCause("Fire"),100);
        var lava=new Damage(Damage.NULL_SOURCE,new DamageCause("Lava"),100);
        assertNull(HytaleDamageAdapter.eligibleResistanceChannel(nativeFire));
        var direct=new HytaleDamageMetadata(UUID.randomUUID(),"root","skill","contact",100,100,
                "effect",true,HytaleDamageMetadata.Origin.DIRECT);
        nativeFire.putMetaObject(HytaleDamageAdapter.RPG_METADATA,new com.google.gson.Gson().toJson(direct));
        lava.putMetaObject(HytaleDamageAdapter.RPG_METADATA,new com.google.gson.Gson().toJson(direct));
        assertEquals(GearCombatEffects.Channel.FIRE,HytaleDamageAdapter.eligibleResistanceChannel(nativeFire));
        assertNull(HytaleDamageAdapter.eligibleResistanceChannel(lava));
        var periodic=new Damage(Damage.NULL_SOURCE,new DamageCause("Fire"),100);
        periodic.putMetaObject(HytaleDamageAdapter.RPG_METADATA,new com.google.gson.Gson().toJson(
                new HytaleDamageMetadata(direct.actorId(),"root","skill","contact",100,100,
                        "effect",false,HytaleDamageMetadata.Origin.PERIODIC)));
        assertNull(HytaleDamageAdapter.eligibleResistanceChannel(periodic));
    }
    @Test void conditionalRulesMutateTheProductionNativeGatherAmountAndRespectTheirBoundaries(){
        for(int id=41;id<=52;id++){
            String affix="WA-"+id;var equipped=item(affix,false);var control=item(affix,true);
            var hit=attack(equipped,100);var base=attack(control,100);
            var positive=facts(id,true);var negative=facts(id,false);
            assertTrue(GearHitConditions.present(hit));assertFalse(GearHitConditions.present(base));
            assertEquals(equipped.affixes().getFirst().value()/100,GearHitConditions.increased(hit,positive),1e-9);
            assertEquals(0,GearHitConditions.increased(hit,negative));
            assertEquals(0,GearHitConditions.increased(base,positive));
            var damage=new Damage(Damage.NULL_SOURCE,new DamageCause("Physical"),100);
            HytaleDamageAdapter.attachGearHit(damage,new HytaleDamageAdapter.GearHitSource(hit,"root-1/victim",
                    GearCombatEffects.Channel.PHYSICAL));
            assertEquals(equipped.affixes().getFirst().value()/100,HytaleConditionalDamage.gearGather(damage,positive),1e-9);
            assertEquals(100*(1+equipped.affixes().getFirst().value()/100),damage.getAmount(),.0001);
            assertEquals(0,HytaleConditionalDamage.gearGather(damage,positive));
            assertEquals(100*(1+equipped.affixes().getFirst().value()/100),damage.getAmount(),.0001);
            assertEquals(0,HytaleConditionalDamage.gearGather(new Damage(Damage.NULL_SOURCE,new DamageCause("Physical"),100),positive));
        }
    }
    private static GearHitConditions.Context facts(int id,boolean yes){
        Vec3 source=new Vec3(0,0,0),target=new Vec3(id==41?(yes?4:4.01):id==42?(yes?12:11.99):2,0,0);
        Vec3 forward=id==43?(yes?new Vec3(1,0,0):new Vec3(-1,0,0)):Vec3.FORWARD;
        double hp=id==44?(yes?29:30):id==45?(yes?80:79):50;
        String status=switch(id){case 46->"STUN";case 47->"BURN";case 48->"CHILL";case 49->"ELECTRIFIED";
            case 50->"POISON";case 51->"BLEED";default->null;};
        return new GearHitConditions.Context(source,target,forward,hp,100,yes&&status!=null?Set.of(status):Set.of(),id==52&&yes);
    }
    @Test void gearConditionAddsToExistingPassiveGatherInsteadOfMultiplyingIt(){
        var finishing=item("WA-044",false);var hit=attack(finishing,100);
        var damage=new Damage(Damage.NULL_SOURCE,new DamageCause("Physical"),100);
        HytaleDamageAdapter.attachGearHit(damage,new HytaleDamageAdapter.GearHitSource(hit,"contact",GearCombatEffects.Channel.PHYSICAL));
        var meta=new HytaleDamageMetadata(UUID.randomUUID(),"root-1","skill","contact",100,29);
        damage.putMetaObject(HytaleDamageAdapter.RPG_METADATA,new com.google.gson.Gson().toJson(meta));
        HytaleConditionalDamage.attach(damage,new com.inigmasgames.hytalerpg.combat.damage.ConditionalDamage(
                new com.inigmasgames.hytalerpg.domain.HitConditionModifiers(true,false),
                com.inigmasgames.hytalerpg.combat.damage.ModifierBuckets.NONE,100,100));
        HytaleConditionalDamage.gather(damage,29,100,Set.of());
        HytaleConditionalDamage.gearGather(damage,facts(44,true));
        assertEquals(135+finishing.affixes().getFirst().value(),damage.getAmount(),.0001);
    }
    @Test void defenseRatingRoundTripsAndLocalShieldPrecedesGlobal(){
        for(int level:new int[]{1,50,99})for(double p:new double[]{0,.1,.3,.6}){
            var view=GearDefenseEffects.resolve(GearEffectSnapshot.EMPTY,level,p,0,0);
            assertEquals(p,view.managedProtection(),1e-10);
        }
        var shield=item("WA-069",false);var baseline=item("WA-069",true);
        var treated=GearDefenseEffects.resolve(new GearEffectSnapshot(List.of(shield)),1,0,0,0);
        var untreated=GearDefenseEffects.resolve(new GearEffectSnapshot(List.of(baseline)),1,0,0,0);
        assertTrue(treated.shieldRating()>untreated.shieldRating());
        var reinforced=item("WA-070",false);var reinforcedControl=item("WA-070",true);
        assertTrue(GearDefenseEffects.resolve(new GearEffectSnapshot(List.of(reinforced)),1,0,0,0).shieldRating()
                >GearDefenseEffects.resolve(new GearEffectSnapshot(List.of(reinforcedControl)),1,0,0,0).shieldRating());
        var global=item("WA-071",false);var globalControl=item("WA-071",true);
        assertTrue(GearDefenseEffects.resolve(new GearEffectSnapshot(List.of(global)),1,.1,0,0).totalRating()
                >GearDefenseEffects.resolve(new GearEffectSnapshot(List.of(globalControl)),1,.1,0,0).totalRating());
        var all=item("WA-078",false);
        for(var channel:GearCombatEffects.Channel.values())if(channel!=GearCombatEffects.Channel.PHYSICAL)
            assertEquals(all.affixes().getFirst().value()/100,GearCombatEffects.resistance(new GearEffectSnapshot(List.of(all)),channel,0),1e-9);
        assertEquals(1.5,GearDefenseEffects.criticalTakenMultiplier(GearEffectSnapshot.EMPTY,1.5));
        var composed=item("WA-084",false);var composedControl=item("WA-084",true);
        assertEquals(150-50*composed.affixes().getFirst().value()/100,
                GearDefenseEffects.criticalAmount(new GearEffectSnapshot(List.of(composed)),100,150),1e-9);
        assertEquals(150,GearDefenseEffects.criticalAmount(new GearEffectSnapshot(List.of(composedControl)),100,150));
        assertEquals(100,GearDefenseEffects.criticalAmount(new GearEffectSnapshot(List.of(composed)),100,100));
    }
    @Test void nativeManagedDamageSequenceClaimsEachChannelOnceWithOneFrozenContact(){
        var gear=item("WA-019",false);var hit=attack(gear,100);var actor=UUID.randomUUID();
        ManagedGearDamageInteraction.register(actor,hit,Set.of("Physical","Fire"),com.hypixel.hytale.protocol.InteractionType.Primary);
        try {
            var unrelated=new Damage(Damage.NULL_SOURCE,new DamageCause("Fire"),8);
            unrelated.putMetaObject(Damage.INTERACTION_TYPE,com.hypixel.hytale.protocol.InteractionType.Primary);
            assertNull(ManagedGearDamageInteraction.claim(unrelated,actor));
            var first=new Damage(Damage.NULL_SOURCE,new DamageCause("Fire"),8);
            first.putMetaObject(Damage.INTERACTION_TYPE,com.hypixel.hytale.protocol.InteractionType.Primary);
            first.putMetaObject(com.hypixel.hytale.server.core.modules.entity.damage.DamageCalculatorSystems.DAMAGE_SEQUENCE,
                    new com.hypixel.hytale.server.core.modules.entity.damage.DamageCalculatorSystems.DamageSequence(
                            new com.hypixel.hytale.server.core.modules.entity.damage.DamageCalculatorSystems.Sequence(),
                            new ManagedGearDamageInteraction.Calculator()));
            assertSame(hit,ManagedGearDamageInteraction.claim(first,actor));
            var second=new Damage(Damage.NULL_SOURCE,new DamageCause("Physical"),100);
            second.putMetaObject(Damage.INTERACTION_TYPE,com.hypixel.hytale.protocol.InteractionType.Primary);
            assertSame(hit,ManagedGearDamageInteraction.claim(second,actor));
            assertNull(ManagedGearDamageInteraction.claim(second,actor));
            assertEquals(0,ManagedGearDamageInteraction.pending(actor));
        } finally {ManagedGearDamageInteraction.forget(actor);}
    }
}
