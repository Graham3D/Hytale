package com.inigmasgames.hytalerpg.gear;

import com.inigmasgames.hytalerpg.combat.attribute.RpgAttribute;
import com.inigmasgames.hytalerpg.combat.status.GearStatusRuntime;
import com.inigmasgames.hytalerpg.combat.status.PeriodicStatusRuntime;
import com.inigmasgames.hytalerpg.combat.status.ControlProfile;
import com.inigmasgames.hytalerpg.combat.status.RpgStatusType;
import com.inigmasgames.hytalerpg.combat.status.StatusService;
import com.inigmasgames.hytalerpg.combat.balance.CombatBalanceProfile;
import com.inigmasgames.hytalerpg.execution.hytale.NativeAffixHostileDisplacement;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Isolated, same-carrier contacts at the current production combat and status owners. */
class MasterAffixCombatCompletionTest {
    private static final GearCatalog CATALOG = GearCatalog.load();
    private static final GearAffixQaSuite QA = new GearAffixQaSuite(CATALOG);
    private static final Map<RpgAttribute,Integer> STATS = Map.of(RpgAttribute.STR,500,
            RpgAttribute.DEX,500,RpgAttribute.INT,500,RpgAttribute.WIS,500,RpgAttribute.LUCK,500);
    private static final Set<String> CAPABILITIES = CATALOG.affixes().stream()
            .map(GearCatalog.Affix::id).collect(java.util.stream.Collectors.toUnmodifiableSet());

    private static GearInstance item(String id,String baseId) {
        var base=CATALOG.base(baseId);
        var definition=CATALOG.affix(id);
        assertTrue(new GearBindings().require(baseId).mapped(),baseId);
        assertTrue(GearDropGenerator.eligible(definition,base),id+" on "+baseId);
        var fixture=QA.fixtures().stream().filter(f->f.fixtureId().equals("ab-"+id.toLowerCase()+"-affixed"))
                .findFirst().orElseThrow();
        var authored=QA.preview(fixture).affixes().getFirst();
        var roll=new GearInstance.AffixRoll(id,definition.side(),definition.exclusionGroup(),authored.tier(),
                authored.value(),new GearRequirements.Gate(1,Map.of()),definition.name(),definition.name());
        return GearInstance.authoredQa(base,UUID.randomUUID(),base.sourceWindow().getLast(),1000,
                GearRarity.UNCOMMON,List.of(roll),BigDecimal.ZERO);
    }
    private static GearInstance plain(GearInstance affixed) {
        var base=CATALOG.base(affixed.baseId());
        return GearInstance.authoredQa(base,UUID.randomUUID(),base.sourceWindow().getLast(),1000,
                GearRarity.UNCOMMON,List.of(),BigDecimal.ZERO);
    }
    private static GearEffectSnapshot accepted(GearInstance... items) {
        var slots=java.util.Arrays.stream(items).map(g->new GearEquipmentResolution.Candidate(g,true,true,true)).toList();
        var result=GearEquipmentResolution.resolve(99,STATS,slots,CAPABILITIES);
        assertEquals(List.of(items),result.validItems(),result.rejected().toString());
        return result.effects().snapshot();
    }
    private static GearCombatEffects.Hit hit(GearEffectSnapshot snapshot,GearInstance source,double power) {
        return GearCombatEffects.attack(snapshot,source.identity(),"accepted/contact",power,1,true,false,
                0,null,0,1.5,false);
    }
    private static GearInstance mapped(String id) {
        var definition=CATALOG.affix(id);var bindings=new GearBindings();
        var base=CATALOG.bases().stream().filter(b->bindings.require(b.id()).mapped()
                &&GearDropGenerator.eligible(definition,b)).filter(b->b.id().endsWith(".nm"))
                .findFirst().orElseThrow(()->new AssertionError("No mapped legal carrier for "+id));
        return item(id,base.id());
    }

    @Test void wa003MappedLegalFocusChangesAcceptedNativeMagicContactOnly() {
        var focus=item("WA-003","gm.wand_oak.nm");
        var control=plain(focus);
        var equipped=accepted(focus);var unrolled=accepted(control);
        var actual=ManagedCarrierDamageInteraction.resolve(equipped,focus,"focus-contact",1,0,false);
        var baseline=ManagedCarrierDamageInteraction.resolve(unrolled,control,"focus-contact",1,0,false);
        assertEquals(focus.affixes().getFirst().value(),actual.amount(GearCombatEffects.Channel.PHYSICAL)
                -baseline.amount(GearCombatEffects.Channel.PHYSICAL),1e-8);
        assertEquals(actual.amount(GearCombatEffects.Channel.PHYSICAL),
                ManagedCarrierDamageInteraction.resolve(equipped,focus,"focus-contact",1,0,true)
                        .amount(GearCombatEffects.Channel.PHYSICAL),1e-8);
        assertThrows(IllegalArgumentException.class,()->ManagedCarrierDamageInteraction.resolve(
                unrolled,focus,"focus-contact",1,0,false));
        assertThrows(IllegalArgumentException.class,()->ManagedCarrierDamageInteraction.resolve(
                GearEffectSnapshot.EMPTY,focus,"focus-contact",1,0,false));
        assertEquals(0,ManagedCarrierDamageInteraction.resolve(equipped,focus,"focus-contact",0,0,false)
                .amount(GearCombatEffects.Channel.PHYSICAL));
    }

    @Test void wa157And158IndependentlyMoveNativePhysicalEndpoints() {
        for(String id:List.of("WA-157","WA-158")) {
            var weapon=item(id,"gm.sword_iron.nm");var control=plain(weapon);
            var range=GearCombatEffects.physical(weapon);var ordinary=GearCombatEffects.physical(control);
            double value=weapon.affixes().getFirst().value();
            if(id.equals("WA-157")) {
                assertEquals(value,range.minimum()-ordinary.minimum(),.100001);
                assertEquals(ordinary.maximum(),range.maximum(),1e-8);
            } else {
                assertEquals(ordinary.minimum(),range.minimum(),1e-8);
                assertEquals(value,range.maximum()-ordinary.maximum(),.100001);
            }
            var source=accepted(weapon);var unrolled=accepted(control);
            double endpoint=id.equals("WA-157")?range.minimum():range.maximum();
            String root=null;
            for(int n=0;n<100_000;n++) {
                String candidate=id+"/native-contact/"+n;
                if(ManagedGearDamageInteraction.samplePower(weapon,candidate)==endpoint) {
                    root=candidate;break;
                }
            }
            assertNotNull(root,id+" endpoint was not sampled by the native power owner");
            double actualPower=ManagedGearDamageInteraction.samplePower(weapon,root);
            double plainPower=ManagedGearDamageInteraction.samplePower(control,root);
            assertEquals(endpoint,actualPower,1e-8,id);
            assertTrue(actualPower>=range.minimum()&&actualPower<=range.maximum());
            assertTrue(plainPower>=ordinary.minimum()&&plainPower<=ordinary.maximum());
            assertEquals(actualPower,hit(source,weapon,actualPower).amount(GearCombatEffects.Channel.PHYSICAL),1e-8);
            assertEquals(plainPower,hit(unrolled,control,plainPower).amount(GearCombatEffects.Channel.PHYSICAL),1e-8);
            assertThrows(IllegalArgumentException.class,()->hit(unrolled,weapon,actualPower));
        }
    }

    @Test void shieldAffixesIndependentlyChangeDefenseOwnerProjectionAndWrongSlotCannotApply() {
        for(String id:List.of("WA-069","WA-070","WA-071")) {
            var shield=item(id,"gm.shield_iron.nm");var control=plain(shield);
            var active=accepted(shield);var baseline=accepted(control);
            var improved=GearDefenseEffects.resolve(active,60,.10,0,0);
            var ordinary=GearDefenseEffects.resolve(baseline,60,.10,0,0);
            assertTrue(improved.managedProtection()>ordinary.managedProtection(),id);
            assertTrue(GearDefenseEffects.reduction(improved,60,0)>
                    GearDefenseEffects.reduction(ordinary,60,0),id);
            var denied=GearEquipmentResolution.resolve(99,STATS,List.of(
                    new GearEquipmentResolution.Candidate(shield,true,true,false)),CAPABILITIES);
            assertEquals("WRONG_SLOT",denied.rejected().get(shield.identity()));
            assertEquals(.10,GearDefenseEffects.resolve(
                    denied.effects().snapshot(),60,.10,0,0).managedProtection(),1e-9,id);
            assertEquals(ordinary.managedProtection(),GearDefenseEffects.resolve(
                    accepted(control),60,.10,0,0).managedProtection(),1e-9,id);
        }
    }

    @Test void ga159And160IndependentlyChangeNativeArmorProjection() {
        for(String id:List.of("GA-159","GA-160")) {
            var armor=item(id,"gm.plate_iron.chest.nm");var control=plain(armor);
            double improved=GearAffixRuntime.protection(armor),ordinary=GearAffixRuntime.protection(control);
            assertTrue(improved>ordinary,id);
            var effect=GearDefenseEffects.resolve(accepted(armor),60,Math.min(.60,improved/100),0,0);
            var baseline=GearDefenseEffects.resolve(accepted(control),60,Math.min(.60,ordinary/100),0,0);
            assertTrue(effect.managedProtection()>baseline.managedProtection(),id);
            var denied=GearEquipmentResolution.resolve(99,STATS,List.of(
                    new GearEquipmentResolution.Candidate(armor,true,false,true)),CAPABILITIES);
            assertEquals("BROKEN",denied.rejected().get(armor.identity()));
            assertTrue(denied.effects().snapshot().empty());
            assertEquals(0,GearDefenseEffects.resolve(denied.effects().snapshot(),60,0,0,0)
                    .managedProtection(),1e-9);
        }
    }

    @Test void wa066And067SeparatelyIncreaseOnlyTheirPeriodicTicks() {
        for(String id:List.of("WA-066","WA-067")) {
            var gear=item(id,"gm.sword_iron.nm");var control=plain(gear);
            var kind=id.equals("WA-066")?PeriodicStatusRuntime.Kind.POISON:PeriodicStatusRuntime.Kind.BLEED;
            var other=id.equals("WA-066")?PeriodicStatusRuntime.Kind.BLEED:PeriodicStatusRuntime.Kind.POISON;
            var owner=UUID.randomUUID();var victim=UUID.randomUUID();
            var source=new PeriodicStatusRuntime.Source(owner,"accepted-native-contact",victim,kind);
            var effect=new PeriodicStatusRuntime<String,String>();var ordinary=new PeriodicStatusRuntime<String,String>();
            var altered=new ArrayList<Double>();var plainTicks=new ArrayList<Double>();
            PeriodicStatusRuntime.Port<String,String> boosted=port(altered),baseline=port(plainTicks);
            var type=id.equals("WA-066")?RpgStatusType.POISON:RpgStatusType.BLEED;
            GearStatusRuntime.PeriodicAdmission boostedAdmission=(admittedKind,contact,snapshot)->
                    GearStatusRuntime.applyPeriodic(effect,source,"contact","victim",10,10,2,
                            1,1,0,boosted,snapshot);
            GearStatusRuntime.PeriodicAdmission baselineAdmission=(admittedKind,contact,snapshot)->
                    GearStatusRuntime.applyPeriodic(ordinary,source,"contact","victim",10,10,2,
                            1,1,0,baseline,snapshot);
            var positiveContact=new GearStatusRuntime.AppliedHit(owner,"positive","contact",victim,
                    true,true,true,false,10,1,hit(accepted(gear),gear,100).amounts());
            var controlContact=new GearStatusRuntime.AppliedHit(owner,"unrolled","contact",victim,
                    true,true,true,false,10,1,hit(accepted(control),control,100).amounts());
            var statuses=new StatusService(CombatBalanceProfile.loadCanonical(),()->0L);
            var contacts=new GearStatusRuntime.Contacts();
            assertEquals("ADMITTED",GearStatusRuntime.admit(statuses,contacts,positiveContact,type,
                    ControlProfile.NORMAL,accepted(gear),accepted(gear),GearEffectSnapshot.EMPTY,
                    0,1,0,0,boostedAdmission).gate());
            assertEquals("ADMITTED",GearStatusRuntime.admit(statuses,contacts,controlContact,type,
                    ControlProfile.NORMAL,accepted(control),accepted(control),GearEffectSnapshot.EMPTY,
                    0,1,0,0,baselineAdmission).gate());
            assertTrue(effect.sourceView(source,0).orElseThrow().coefficientPerSecond()>
                    ordinary.sourceView(source,0).orElseThrow().coefficientPerSecond(),id);
            effect.tick(owner,1,boosted);ordinary.tick(owner,1,baseline);
            assertTrue(altered.getFirst()>plainTicks.getFirst(),id);
            assertEquals("INELIGIBLE_HIT",GearStatusRuntime.admit(statuses,contacts,
                    new GearStatusRuntime.AppliedHit(owner,"wrong-channel","contact",victim,
                            true,true,true,false,10,1,Map.of(GearCombatEffects.Channel.FIRE,100d)),
                    type,ControlProfile.NORMAL,accepted(gear),accepted(gear),GearEffectSnapshot.EMPTY,
                    0,1,0,0,boostedAdmission).gate());
            assertEquals("INELIGIBLE_HIT",GearStatusRuntime.admit(statuses,contacts,
                    new GearStatusRuntime.AppliedHit(owner,"child/WA141/NoProc","contact",victim,
                            true,true,false,false,10,1,positiveContact.appliedChannels()),
                    type,ControlProfile.NORMAL,accepted(gear),accepted(gear),GearEffectSnapshot.EMPTY,
                    0,1,0,0,boostedAdmission).gate());
            assertEquals(0,GearStatusRuntime.increasedDps(accepted(gear),other),1e-9,id+" wrong status");
            assertEquals(0,GearStatusRuntime.increasedDps(GearStatusRuntime.sourceScoped(accepted(gear),control.identity()),kind),1e-9);
        }
    }
    @Test void wa141QualifiedTwinAddsDistinctFortyPercentNoProcContact() {
        var main=item("WA-141","gm.daggers_iron.nm");var offhand=plain(main);
        var unrolled=plain(main);var accepted=accepted(main,offhand);
        var profile=NativeGearActionProfiles.primary(accepted,main.identity());
        assertTrue(profile.rootId().contains("Twin"));
        assertTrue(NativeTwinAssaultEligibility.accepts(accepted,main,offhand,0));
        assertFalse(NativeTwinAssaultEligibility.accepts(accepted(main),main,offhand,0));
        assertFalse(NativeTwinAssaultEligibility.accepts(accepted(unrolled,offhand),unrolled,offhand,0));
        assertFalse(NativeTwinAssaultEligibility.accepts(accepted,offhand,main,0));
        var wrong=plain(item("WA-141","gm.daggers_adamantite.nm"));
        assertFalse(NativeTwinAssaultEligibility.accepts(accepted,main,wrong,0));
        String root="accepted-twin-contact";
        double mainPower=ManagedGearDamageInteraction.samplePower(main,root);
        double offhandPower=ManagedGearDamageInteraction.samplePower(offhand,root+"/offhand");
        var first=GearCombatEffects.attack(accepted,main.identity(),root,mainPower,1,true,false,
                0,null,0,1.5,false);
        var child=GearCombatEffects.attack(accepted,offhand.identity(),root+"/WA141/NoProc",
                offhandPower,.4,true,false,0,null,1,1.5,false);
        assertEquals(main.identity(),first.itemId());
        assertEquals(mainPower,first.amount(GearCombatEffects.Channel.PHYSICAL),1e-8);
        assertEquals(offhand.identity(),child.itemId());
        assertEquals(offhandPower*.4,child.amount(GearCombatEffects.Channel.PHYSICAL),1e-8);
        assertFalse(child.critical());
        assertFalse(ManagedGearDamageInteraction.noProcChild(first));
        assertTrue(ManagedGearDamageInteraction.noProcChild(child));
        assertThrows(IllegalArgumentException.class,()->GearCombatEffects.attack(accepted,wrong.identity(),
                root+"/WA141/NoProc",offhandPower,.4,true,false,0,null,0,1.5,false));
    }
    @Test void localPhysicalGlobalPhysicalAndElementalIncreasesKeepSourceAndChannelBoundaries() {
        for(String id:List.of("WA-001","WA-002","WA-006")) {
            var gear=item(id,"gm.sword_iron.nm");var control=plain(gear);
            double power=id.equals("WA-006")?100:GearCombatEffects.physical(gear).minimum();
            double baseline=id.equals("WA-006")?100:GearCombatEffects.physical(control).minimum();
            assertTrue(hit(accepted(gear),gear,power).amount(GearCombatEffects.Channel.PHYSICAL)>
                    hit(accepted(control),control,baseline).amount(GearCombatEffects.Channel.PHYSICAL),id);
            assertThrows(IllegalArgumentException.class,()->hit(accepted(control),gear,power),id);
        }
        var prism=mapped("WA-007");var plainPrism=plain(prism);
        var fire=item("WA-019","gm.sword_iron.nm");
        var elemental=hit(accepted(prism,fire),fire,100);
        var baseline=hit(accepted(plainPrism,fire),fire,100);
        assertTrue(elemental.amount(GearCombatEffects.Channel.FIRE)>
                baseline.amount(GearCombatEffects.Channel.FIRE));
        assertEquals(baseline.amount(GearCombatEffects.Channel.PHYSICAL),
                elemental.amount(GearCombatEffects.Channel.PHYSICAL),1e-8);
    }

    @Test void localElementalFlatsAndPenetrationUseTheirOwnNativeChannel() {
        var channels=List.of(GearCombatEffects.Channel.WIND,GearCombatEffects.Channel.WATER,
                GearCombatEffects.Channel.FIRE,GearCombatEffects.Channel.EARTH,
                GearCombatEffects.Channel.LIGHTNING,GearCombatEffects.Channel.VOID);
        for(int i=0;i<channels.size();i++) {
            var channel=channels.get(i);
            String flatId="WA-"+String.format("%03d",17+i);
            var flat=item(flatId,"gm.sword_iron.nm");var unrolled=plain(flat);
            var equipped=accepted(flat,unrolled);
            var actual=hit(equipped,flat,100);var wrongItem=hit(equipped,unrolled,100);
            assertTrue(actual.amount(channel)>wrongItem.amount(channel),flatId);
            for(var other:channels)if(other!=channel)
                assertEquals(wrongItem.amount(other),actual.amount(other),1e-8,flatId+" wrong channel");
            assertThrows(IllegalArgumentException.class,()->hit(accepted(unrolled),flat,100),flatId);
            String penetrationId="WA-"+String.format("%03d",29+i);
            var pen=mapped(penetrationId);var penControl=plain(pen);
            var modified=hit(accepted(pen),pen,100);var ordinary=hit(accepted(penControl),penControl,100);
            assertTrue(modified.penetration(channel)>ordinary.penetration(channel),penetrationId);
            assertTrue(GearCombatEffects.resisted(100,GearEffectSnapshot.EMPTY,channel,.5,
                    modified.penetration(channel))>
                    GearCombatEffects.resisted(100,GearEffectSnapshot.EMPTY,channel,.5,
                            ordinary.penetration(channel)),penetrationId);
            for(var other:channels)if(other!=channel)
                assertEquals(ordinary.penetration(other),modified.penetration(other),1e-8,
                        penetrationId+" wrong channel");
        }
    }

    @Test void criticalDotDurationAllResistanceAndDisplacementHaveUnrolledAndInvalidConditions() {
        var critical=mapped("WA-010");var criticalControl=plain(critical);
        String selected=null;
        for(int i=0;i<10_000;i++) {
            String root="crit-condition/"+i;
            var with=GearCombatEffects.attack(accepted(critical),critical.identity(),root,100,1,
                    true,false,0,null,0,1.5,true);
            var without=GearCombatEffects.attack(accepted(criticalControl),criticalControl.identity(),root,100,1,
                    true,false,0,null,0,1.5,true);
            if(with.critical()&&!without.critical()) {selected=root;break;}
        }
        assertNotNull(selected);
        assertFalse(GearCombatEffects.attack(accepted(critical),critical.identity(),selected,100,1,
                true,false,0,null,0,1.5,false).critical());

        for(String id:List.of("WA-013","WA-065","WA-068")) {
            var gear=mapped(id);var control=plain(gear);
            var kind=PeriodicStatusRuntime.Kind.BURN;
            var source=new PeriodicStatusRuntime.Source(UUID.randomUUID(),"accepted",UUID.randomUUID(),kind);
            var boosted=new PeriodicStatusRuntime<String,String>();var baseline=new PeriodicStatusRuntime<String,String>();
            var changed=new ArrayList<Double>();var plainTicks=new ArrayList<Double>();
            GearStatusRuntime.applyPeriodic(boosted,source,"hit","victim",10,10,4,1,1,0,port(changed),accepted(gear));
            GearStatusRuntime.applyPeriodic(baseline,source,"hit","victim",10,10,4,1,1,0,port(plainTicks),accepted(control));
            if(id.equals("WA-068")) {
                assertTrue(boosted.sourceView(source,0).orElseThrow().remainingSeconds()>
                        baseline.sourceView(source,0).orElseThrow().remainingSeconds());
                assertEquals(10,boosted.sourceView(source,0).orElseThrow().coefficientPerSecond(),1e-8);
            } else {
                assertTrue(boosted.sourceView(source,0).orElseThrow().coefficientPerSecond()>
                        baseline.sourceView(source,0).orElseThrow().coefficientPerSecond(),id);
                boosted.tick(source.owner(),1,port(changed));baseline.tick(source.owner(),1,port(plainTicks));
                assertTrue(changed.getFirst()>plainTicks.getFirst(),id);
            }
            if(id.equals("WA-065")) assertEquals(0,GearStatusRuntime.increasedDps(
                    accepted(gear),PeriodicStatusRuntime.Kind.POISON),1e-8);
        }

        var resistance=mapped("WA-078");var noResistance=plain(resistance);
        for(var channel:GearCombatEffects.Channel.values())if(channel!=GearCombatEffects.Channel.PHYSICAL)
            assertTrue(GearCombatEffects.resisted(100,accepted(resistance),channel,0,0)<
                    GearCombatEffects.resisted(100,accepted(noResistance),channel,0,0));
        assertEquals(100,GearCombatEffects.resisted(100,accepted(resistance),
                GearCombatEffects.Channel.PHYSICAL,0,0));

        var anchor=mapped("WA-082");var noAnchor=plain(anchor);
        var start=Vec3.ZERO;var center=new Vec3(-1,0,0);
        var reduce=NativeAffixHostileDisplacement.plan(start,center,false,4,ControlProfile.NORMAL,true,
                accepted(anchor),(point,segment)->1,point->true);
        var ordinary=NativeAffixHostileDisplacement.plan(start,center,false,4,ControlProfile.NORMAL,true,
                accepted(noAnchor),(point,segment)->1,point->true);
        assertTrue(reduce.distance()<ordinary.distance());
        var position=new com.hypixel.hytale.server.core.modules.entity.component.TransformComponent();
        NativeAffixHostileDisplacement.applyNpcDestination(position,reduce);
        assertEquals(reduce.destination().x(),position.getPosition().x,1e-9);
        NativeAffixHostileDisplacement.applyNpcDestination(position,ordinary);
        assertEquals(ordinary.destination().x(),position.getPosition().x,1e-9);
        var blocked=NativeAffixHostileDisplacement.plan(start,center,false,4,ControlProfile.NORMAL,true,
                accepted(anchor),(point,segment)->0,point->true);
        NativeAffixHostileDisplacement.applyNpcDestination(position,blocked);
        assertEquals(ordinary.destination().x(),position.getPosition().x,1e-9,"blocked movement leaves native position intact");
        assertEquals(ordinary.distance(),NativeAffixHostileDisplacement.plan(start,center,false,4,
                ControlProfile.NORMAL,true,GearEffectSnapshot.EMPTY,(point,segment)->1,point->true).distance(),1e-8);
    }
    @Test void wa134BleedAdmissionHasUnrolledWrongChannelAndNoProcControls() {
        var rending=mapped("WA-134");var control=plain(rending);
        var owner=UUID.randomUUID();var victim=UUID.randomUUID();
        var statuses=new StatusService(CombatBalanceProfile.loadCanonical(),()->0L);
        var contacts=new GearStatusRuntime.Contacts();
        var runtime=new PeriodicStatusRuntime<String,String>();
        var delivered=new ArrayList<Double>();
        var port=port(delivered);
        GearStatusRuntime.PeriodicAdmission periodic=(kind,applied,snapshot)->
                GearStatusRuntime.applyPeriodic(runtime,new PeriodicStatusRuntime.Source(owner,"native-bleed",victim,kind),
                        "contact","victim",10,10,4,1,1,0,port,snapshot);
        var rendingHit=hit(accepted(rending),rending,100);
        GearStatusRuntime.AppliedHit physical=new GearStatusRuntime.AppliedHit(owner,"rending-root","contact",
                victim,true,true,true,false,10,1,rendingHit.amounts());
        var admitted=GearStatusRuntime.admit(statuses,contacts,physical,RpgStatusType.BLEED,
                ControlProfile.NORMAL,accepted(rending),accepted(rending),GearEffectSnapshot.EMPTY,
                0,0,0,0,periodic);
        assertEquals("ADMITTED",admitted.gate());
        assertEquals("APPLIED",admitted.periodicResult());
        assertEquals(.5,statuses.healthRegenerationFactor(victim),1e-8);
        var nativeRegen=new com.hypixel.hytale.server.core.modules.entitystats.asset.EntityStatType.Regenerating(
                1,10,com.hypixel.hytale.server.core.modules.entitystats.asset.EntityStatType.Regenerating.RegenType.ADDITIVE,null,null);
        var nativeStat=new com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue(0,
                new com.hypixel.hytale.server.core.modules.entitystats.asset.EntityStatType("Health",50,0,100,false,
                        new com.hypixel.hytale.server.core.modules.entitystats.asset.EntityStatType.Regenerating[]{nativeRegen},null,null,null)) {};
        var suppressed=new com.inigmasgames.hytalerpg.combat.hytale.NativeHealthRegenerationSuppression.Entry(
                new com.hypixel.hytale.server.core.modules.entitystats.RegeneratingValue(nativeRegen),victim,statuses);
        var normal=new com.inigmasgames.hytalerpg.combat.hytale.NativeHealthRegenerationSuppression.Entry(
                new com.hypixel.hytale.server.core.modules.entitystats.RegeneratingValue(nativeRegen),UUID.randomUUID(),statuses);
        assertEquals(5,suppressed.regenerate(null,null,java.time.Instant.EPOCH,.1f,nativeStat,0),1e-6);
        assertEquals(10,normal.regenerate(null,null,java.time.Instant.EPOCH,.1f,nativeStat,0),1e-6);
        var plainHit=new GearStatusRuntime.AppliedHit(owner,"control-root","contact",victim,
                true,true,true,false,10,1,hit(accepted(control),control,100).amounts());
        assertEquals("CHANCE_MISS",GearStatusRuntime.admit(statuses,contacts,plainHit,RpgStatusType.BLEED,
                ControlProfile.NORMAL,accepted(control),accepted(control),GearEffectSnapshot.EMPTY,
                0,0,0,0,periodic).gate());
        var wrongChannel=new GearStatusRuntime.AppliedHit(owner,"wrong-channel","contact",victim,
                true,true,true,false,10,1,Map.of(GearCombatEffects.Channel.FIRE,100d));
        assertEquals("INELIGIBLE_HIT",GearStatusRuntime.admit(statuses,contacts,wrongChannel,RpgStatusType.BLEED,
                ControlProfile.NORMAL,accepted(rending),accepted(rending),GearEffectSnapshot.EMPTY,
                0,0,0,0,periodic).gate());
        var child=new GearStatusRuntime.AppliedHit(owner,"child/WA141/NoProc","contact",victim,
                true,true,false,false,10,1,rendingHit.amounts());
        assertEquals("INELIGIBLE_HIT",GearStatusRuntime.admit(statuses,contacts,child,RpgStatusType.BLEED,
                ControlProfile.NORMAL,accepted(rending),accepted(rending),GearEffectSnapshot.EMPTY,
                0,0,0,0,periodic).gate());
        assertEquals(1,runtime.size());
    }
    private static final class SignaturePort implements GearSignatureProcRuntime.Port {
        final List<GearSignatureProcRuntime.Child> children=new ArrayList<>();
        int executions;
        final UUID nearby=UUID.randomUUID();
        public void enqueue(GearSignatureProcRuntime.Child child) { children.add(child); }
        public boolean execute(GearSignatureProcRuntime.Contact contact) { executions++;return true; }
        public List<UUID> burstTargets(GearSignatureProcRuntime.Contact contact,double radius,int maximum) {
            return List.of(nearby);
        }
    }
    private static GearSignatureProcRuntime.Contact signatureContact(GearInstance gear,GearEffectSnapshot snapshot,
            UUID world,UUID actor,UUID target,String root,double health,boolean noProc,boolean credited) {
        return new GearSignatureProcRuntime.Contact(world,actor,gear.identity(),target,root,root,snapshot,
                true,true,true,noProc,false,false,10,health,1000,GearSignatureProcRuntime.Kind.COMMON,
                100,Map.of(GearCombatEffects.Channel.PHYSICAL,100d),1,false,false,credited);
    }
    @Test void wa135Through140LegalSignatureContactsHaveUnrolledAndNoProcControls() {
        for(String id:List.of("WA-135","WA-137","WA-138","WA-139","WA-140")) {
            var gear=mapped(id);var control=plain(gear);
            var world=UUID.randomUUID();var actor=UUID.randomUUID();var victim=UUID.randomUUID();
            double health=id.equals("WA-139")?40:500;
            var owner=new GearSignatureProcRuntime(()->0d);
            var ordinary=new GearSignatureProcRuntime(()->0d);
            var childOnly=new GearSignatureProcRuntime(()->0d);
            var positive=new SignaturePort();var plain=new SignaturePort();var noProc=new SignaturePort();
            owner.applied(signatureContact(gear,accepted(gear),world,actor,victim,"positive",health,false,false),1,positive);
            ordinary.applied(signatureContact(control,accepted(control),world,actor,victim,"control",health,false,false),1,plain);
            childOnly.applied(signatureContact(gear,accepted(gear),world,actor,victim,"child",health,true,false),1,noProc);
            assertTrue(plain.children.isEmpty()&&plain.executions==0,id+" unrolled");
            assertTrue(noProc.children.isEmpty()&&noProc.executions==0,id+" NoProc");
            switch(id) {
                case "WA-135" -> assertEquals(GearSignatureProcRuntime.ChildKind.CRUSHING,
                        positive.children.getFirst().kind());
                case "WA-137" -> {
                    assertTrue(positive.children.isEmpty());
                    owner.applied(signatureContact(gear,accepted(gear),world,actor,victim,"next",health,false,false),2,positive);
                    assertEquals(GearSignatureProcRuntime.ChildKind.BARBED,positive.children.getFirst().kind());
                }
                case "WA-138" -> {
                    assertEquals(.85,owner.armorRatingFactor(victim,2),1e-8);
                    assertEquals(1,ordinary.armorRatingFactor(victim,2),1e-8);
                    assertEquals(1,childOnly.armorRatingFactor(victim,2),1e-8);
                }
                case "WA-139" -> assertEquals(1,positive.executions);
                case "WA-140" -> {
                    assertEquals(.95,owner.incomingHitFactor(actor,2,true,false),1e-8);
                    assertEquals(1,ordinary.incomingHitFactor(actor,2,true,false),1e-8);
                    assertEquals(1,owner.incomingHitFactor(actor,2,false,true),1e-8);
                }
                default -> fail(id);
            }
        }
        var deadly=mapped("WA-136");var control=plain(deadly);
        var runtime=new GearSignatureProcRuntime(()->0d);
        assertEquals(200,runtime.deadly(accepted(deadly),deadly.identity(),"positive",100,false,true,false,1),1e-8);
        assertEquals(100,runtime.deadly(accepted(control),control.identity(),"control",100,false,true,false,1),1e-8);
        assertEquals(100,runtime.deadly(accepted(deadly),deadly.identity(),"child",100,false,true,true,1),1e-8);
        assertEquals(100,runtime.deadly(accepted(deadly),deadly.identity(),"critical",100,true,true,false,1),1e-8);
    }
    @Test void wa142And143UseIncomingMeleeAndCreditedKillOnly() {
        var world=UUID.randomUUID();var owner=UUID.randomUUID();var victim=UUID.randomUUID();
        var reflect=mapped("WA-142");var plainReflect=plain(reflect);
        var runtime=new GearSignatureProcRuntime(()->0d);var positive=new SignaturePort();var control=new SignaturePort();
        runtime.received(world,owner,victim,"hit","contact",accepted(reflect),true,true,false,false,
                10,false,1,positive);
        runtime.received(world,owner,victim,"control","contact",accepted(plainReflect),true,true,false,false,
                10,false,2,control);
        runtime.received(world,owner,victim,"reflected","contact",accepted(reflect),true,true,true,false,
                10,false,3,positive);
        assertEquals(1,positive.children.size());
        assertTrue(control.children.isEmpty());
        assertEquals(GearSignatureProcRuntime.ChildKind.RETRIBUTION,positive.children.getFirst().kind());
        var burst=mapped("WA-143");var plainBurst=plain(burst);
        var death=new GearSignatureProcRuntime(()->0d);var triggered=new SignaturePort();var without=new SignaturePort();
        death.creditedKill(signatureContact(burst,accepted(burst),world,owner,victim,"uncredited",0,false,false),1,triggered);
        assertTrue(triggered.children.isEmpty());
        death.creditedKill(signatureContact(plainBurst,accepted(plainBurst),world,owner,victim,"unrolled",0,false,true),1,without);
        death.creditedKill(signatureContact(burst,accepted(burst),world,owner,victim,"NoProc",0,true,true),1,triggered);
        assertTrue(without.children.isEmpty());assertTrue(triggered.children.isEmpty());
        var credited=signatureContact(burst,accepted(burst),world,owner,victim,"credited",0,false,true);
        death.creditedKill(credited,1,triggered);death.creditedKill(credited,1,triggered);
        assertEquals(1,triggered.children.size());
        assertEquals(GearSignatureProcRuntime.ChildKind.KILL_BURST,triggered.children.getFirst().kind());
        assertTrue(triggered.children.getFirst().noProc());
    }
    private static PeriodicStatusRuntime.Port<String,String> port(List<Double> ticks) {
        return new PeriodicStatusRuntime.Port<>() {
            public boolean tick(PeriodicStatusRuntime.Source source,String context,String target,int index,
                                double coefficient,double seconds) { ticks.add(coefficient);return true; }
            public void changed(PeriodicStatusRuntime.Source source,String target,PeriodicStatusRuntime.View view) { }
        };
    }
}
