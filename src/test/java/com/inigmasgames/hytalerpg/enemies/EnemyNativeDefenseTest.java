package com.inigmasgames.hytalerpg.enemies;

import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageSystems.ArmorDamageReduction.ArmorResistanceModifiers;
import com.inigmasgames.hytalerpg.combat.defense.DefenseView;
import com.inigmasgames.hytalerpg.execution.hytale.NativeEnemyArmor;
import com.inigmasgames.hytalerpg.difficulty.MonsterResistanceProfile;
import static com.inigmasgames.hytalerpg.difficulty.MonsterResistanceProfile.Channel.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EnemyNativeDefenseTest {
    private static final DamageCause PHYSICAL=new DamageCause("Physical"),PROJECTILE=new DamageCause("Projectile"),FIRE=new DamageCause("Fire");
    static ArmorResistanceModifiers modifier(float flat,float fraction){
        var result=new ArmorResistanceModifiers();result.flatModifier=flat;result.multiplierModifier=fraction;return result;
    }
    static NativeEnemyArmor.Physical stone(double scale,double broken){
        return new NativeEnemyArmor.Physical(50,new DefenseView.Contributions(0,600*scale,0,broken),false);
    }
    @Test void correctedStoneSkinChangesTheNativeMultiplierOnceForPhysicalAndProjectileOnly(){
        double[] scales={.25,1d/3,3d/7};float[] expected={80,75,70};
        for(int i=0;i<scales.length;i++)for(var cause:List.of(PHYSICAL,PROJECTILE)){
            var map=new HashMap<DamageCause,ArmorResistanceModifiers>();
            NativeEnemyArmor.projectPhysical(cause,map,stone(scales[i],0));
            assertEquals(expected[i],NativeEnemyArmor.applyNative(100,cause,map),.00001);
            NativeEnemyArmor.projectPhysical(FIRE,map,stone(scales[i],0));
            assertEquals(100,NativeEnemyArmor.applyNative(100,FIRE,map));assertFalse(map.containsKey(FIRE));
        }
    }
    @Test void nativeArmorFlatProtectionAndStrongestBreakComposeAtOneBoundary(){
        var map=new HashMap<DamageCause,ArmorResistanceModifiers>();map.put(PHYSICAL,modifier(10,.20f));
        NativeEnemyArmor.projectPhysical(PHYSICAL,map,stone(.25,.25));
        double nativeRating=DefenseView.rating(50,.20f);
        double effective=(nativeRating+150)*.75;
        assertEquals(10,map.get(PHYSICAL).flatModifier);
        assertEquals(90*(1-effective/(600+effective)),NativeEnemyArmor.applyNative(100,PHYSICAL,map),.00001);
        assertEquals(0,NativeEnemyArmor.applyNative(5,PHYSICAL,map));
    }
    @Test void capAndNativeFullProtectionRemainDistinctFromNewImmunity(){
        var map=new HashMap<DamageCause,ArmorResistanceModifiers>();map.put(PHYSICAL,modifier(0,.5f));
        NativeEnemyArmor.projectPhysical(PHYSICAL,map,new NativeEnemyArmor.Physical(50,new DefenseView.Contributions(0,10000,0,0),false));
        assertEquals(.6f,map.get(PHYSICAL).multiplierModifier);assertEquals(40,NativeEnemyArmor.applyNative(100,PHYSICAL,map),.00001);
        for(float nativeValue:new float[]{1,1.2f}){
            map.put(PHYSICAL,modifier(0,nativeValue));NativeEnemyArmor.projectPhysical(PHYSICAL,map,stone(.25,.25));
            assertEquals(nativeValue,map.get(PHYSICAL).multiplierModifier);assertEquals(0,NativeEnemyArmor.applyNative(100,PHYSICAL,map));
        }
        assertThrows(IllegalArgumentException.class,()->NativeEnemyArmor.projectPhysical(PHYSICAL,map,
                new NativeEnemyArmor.Physical(50,DefenseView.Contributions.NONE,true)));
    }
    @Test void identityAndBypassKeepNativeFloatResultsAndUnsupportedContractsRejectBeforeMutation(){
        var map=new HashMap<DamageCause,ArmorResistanceModifiers>();var baseline=modifier(3,.81234f);map.put(PHYSICAL,baseline);
        NativeEnemyArmor.projectPhysical(PHYSICAL,map,new NativeEnemyArmor.Physical(50,DefenseView.Contributions.NONE,false));
        assertSame(baseline,map.get(PHYSICAL));assertEquals(Float.floatToRawIntBits(97f*(1f-.81234f)),Float.floatToRawIntBits(NativeEnemyArmor.applyNative(100,PHYSICAL,map)));
        var bypass=new DamageCause("Physical",null,false,false,true);map.put(bypass,modifier(3,.5f));
        NativeEnemyArmor.projectPhysical(bypass,map,stone(.25,0));assertEquals(100,NativeEnemyArmor.applyNative(100,bypass,map));
        map.put(PHYSICAL,modifier(0,-.2f));assertThrows(IllegalStateException.class,()->NativeEnemyArmor.projectPhysical(PHYSICAL,map,stone(.25,0)));
        assertEquals(-.2f,map.get(PHYSICAL).multiplierModifier);
        map.put(PHYSICAL,modifier(0,.2f));map.get(PHYSICAL).inheritedParentId=FIRE;
        assertThrows(IllegalStateException.class,()->NativeEnemyArmor.projectPhysical(PHYSICAL,map,stone(.25,0)));
        assertEquals(.2f,map.get(PHYSICAL).multiplierModifier);
    }
    @Test void originalFlatAndMultiplierInheritanceOrderIsRetained(){
        var map=new HashMap<DamageCause,ArmorResistanceModifiers>();var first=modifier(4,.2f);first.inheritedParentId=FIRE;
        map.put(PHYSICAL,first);map.put(FIRE,modifier(5,.3f));
        assertEquals(((100f-4)*.8f-5)*.7f,NativeEnemyArmor.applyNative(100,PHYSICAL,map));
    }
    static NativeEnemyArmor.Elemental magic(double addition,double penetration){
        return new NativeEnemyArmor.Elemental(MonsterResistanceProfile.NONE,Map.of(MonsterResistanceProfile.Channel.FIRE,addition),Set.of(),penetration);
    }
    @Test void ordinaryElementalResistanceComposesBeforeItsOneCapAndPenetration(){
        var map=new HashMap<DamageCause,ArmorResistanceModifiers>();map.put(FIRE,modifier(0,.4f));
        NativeEnemyArmor.projectElemental(FIRE,map,magic(.25,.1));
        assertEquals(45,NativeEnemyArmor.applyNative(100,FIRE,map),.00001);
        map.put(FIRE,modifier(0,.7f));NativeEnemyArmor.projectElemental(FIRE,map,magic(.25,0));
        assertEquals(25,NativeEnemyArmor.applyNative(100,FIRE,map),.00001);
        map.put(FIRE,modifier(0,1.1f));NativeEnemyArmor.projectElemental(FIRE,map,magic(0,.1));
        assertEquals(35,NativeEnemyArmor.applyNative(100,FIRE,map),.00001); // Never infer immunity from >=1 ordinary raw.
        var immune=new NativeEnemyArmor.Elemental(MonsterResistanceProfile.NONE,Map.of(),Set.of(MonsterResistanceProfile.Channel.FIRE),.75);
        assertTrue(immune.immune(MonsterResistanceProfile.Channel.FIRE));
        assertThrows(IllegalArgumentException.class,()->NativeEnemyArmor.projectElemental(FIRE,map,immune));
    }
    @Test void inheritedNativePercentagesAreIncludedOnceAndUnknownFlatContractsCannotBeSilentlyFlattened(){
        var parent=new DamageCause("Elemental");var map=new HashMap<DamageCause,ArmorResistanceModifiers>();
        var fire=modifier(4,.2f);fire.inheritedParentId=parent;map.put(FIRE,fire);map.put(parent,modifier(0,.25f));
        NativeEnemyArmor.projectElemental(FIRE,map,magic(.25,.1));
        assertEquals(96*.45,NativeEnemyArmor.applyNative(100,FIRE,map),.00002);
        assertEquals(0,map.get(parent).multiplierModifier);
        fire.multiplierModifier=.2f;map.put(parent,modifier(3,.25f));
        assertThrows(IllegalStateException.class,()->NativeEnemyArmor.projectElemental(FIRE,map,magic(.25,0)));
        assertEquals(.2f,fire.multiplierModifier);assertEquals(.25f,map.get(parent).multiplierModifier);
        map.clear();map.put(FIRE,modifier(0,-.2f));
        assertThrows(IllegalStateException.class,()->NativeEnemyArmor.projectElemental(FIRE,map,magic(.25,0)));
        assertEquals(-.2f,map.get(FIRE).multiplierModifier);
    }
    @Test void affinityIsAFloorAndCanonicalAliasesShareTheSameDefenseFlags(){
        var floor=new MonsterResistanceProfile(Map.of(MonsterResistanceProfile.Channel.FIRE,.6),Set.of());
        var map=new HashMap<DamageCause,ArmorResistanceModifiers>();map.put(FIRE,modifier(0,.4f));
        NativeEnemyArmor.projectElemental(FIRE,map,new NativeEnemyArmor.Elemental(floor,Map.of(MonsterResistanceProfile.Channel.FIRE,.1),Set.of(),0));
        assertEquals(30,NativeEnemyArmor.applyNative(100,FIRE,map),.00001);
        var earth=new NativeEnemyArmor.Elemental(new MonsterResistanceProfile(Map.of(EARTH,.5),Set.of(WATER)),Map.of(),Set.of(VOID),0);
        for(String causeId:List.of("Poison","Earth","RPG_Nature")){
            var cause=new DamageCause(causeId);map.clear();NativeEnemyArmor.projectElemental(cause,map,earth);
            assertEquals(50,NativeEnemyArmor.applyNative(100,cause,map),.00001);
        }
        assertTrue(earth.immune(COLD));assertTrue(earth.immune(NECROTIC));assertFalse(earth.immune(EARTH));
        var unknown=new DamageCause("RPG_Arcane");map.clear();NativeEnemyArmor.projectElemental(unknown,map,earth);
        assertTrue(map.isEmpty());assertEquals(100,NativeEnemyArmor.applyNative(100,unknown,map));
    }
    @Test void foreignNativeEffectsRemainAtTheirExistingAdditiveBoundaryInsteadOfBecomingDefense(){
        // Native Rend (-.15) and food protection (+.05) are nonmanaged effects, not a Defense rating.
        var armor=new HashMap<DamageCause,ArmorResistanceModifiers>();armor.put(PHYSICAL,modifier(4,.2f));
        var all=new HashMap<DamageCause,ArmorResistanceModifiers>();all.put(PHYSICAL,modifier(7,.2f-.15f+.05f));
        NativeEnemyArmor.projectPhysical(PHYSICAL,all,armor,stone(.25,.25));
        double rating=DefenseView.rating(50,.2f),effective=(rating+150)*.75;
        assertEquals(7,all.get(PHYSICAL).flatModifier);assertEquals(.2f,armor.get(PHYSICAL).multiplierModifier);
        assertEquals(93*(1-(effective/(600+effective)-.15+.05)),NativeEnemyArmor.applyNative(100,PHYSICAL,all),.00002);
        all.put(PHYSICAL,modifier(0,-1));
        NativeEnemyArmor.projectPhysical(PHYSICAL,all,Map.of(),stone(.25,0));
        assertEquals(180,NativeEnemyArmor.applyNative(100,PHYSICAL,all),.00002);
        // Native .95 Projectile effect stays .95; it is not silently reduced to the managed .60 cap.
        all.put(PROJECTILE,modifier(0,.95f));
        NativeEnemyArmor.projectPhysical(PROJECTILE,all,Map.of(),stone(.25,0));
        assertEquals(1.15f,all.get(PROJECTILE).multiplierModifier,.00001);
        assertEquals(0,NativeEnemyArmor.applyNative(100,PROJECTILE,all));
    }
    /** No plugin/server construction. Only the native registry and the module's group getter are exercised. */
    static final class RegistryFixture implements AutoCloseable {
        final com.hypixel.hytale.component.ComponentRegistry<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> registry;
        final java.lang.reflect.Field instance;
        final Object previous;
        RegistryFixture() throws Exception {this(new com.hypixel.hytale.component.ComponentRegistry<>());}
        RegistryFixture(com.hypixel.hytale.component.ComponentRegistry<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> registry) throws Exception {
            this.registry=registry;
            var type=com.hypixel.hytale.server.core.modules.entity.damage.DamageModule.class;
            instance=type.getDeclaredField("instance");instance.setAccessible(true);previous=instance.get(null);
            // Allocate a getter-only test fixture; invoking the plugin constructor would require a running server.
            var unsafeType=Class.forName("sun.misc.Unsafe");var field=unsafeType.getDeclaredField("theUnsafe");field.setAccessible(true);
            var module=unsafeType.getMethod("allocateInstance",Class.class).invoke(field.get(null),type);
            var group=type.getDeclaredField("filterDamageGroup");group.setAccessible(true);group.set(module,registry.registerSystemGroup());
            instance.set(null,module);
        }
        @Override public void close() throws Exception {try{registry.shutdown();}finally{instance.set(null,previous);}}
    }
    @Test void nativeRegistryReplacesOneOwnerAndRestoresTheSameOriginalInstance() throws Exception {
        assertEquals("Hytale",com.hypixel.hytale.server.core.modules.entity.damage.DamageModule.MANIFEST.getGroup());
        assertEquals("DamageModule",com.hypixel.hytale.server.core.modules.entity.damage.DamageModule.MANIFEST.getName());
        try(var fixture=new RegistryFixture()){
            var original=new com.hypixel.hytale.server.core.modules.entity.damage.DamageSystems.ArmorDamageReduction();
            fixture.registry.registerSystem(original);
            var adapter=new NativeEnemyArmor((store,target)->null);
            var lease=NativeEnemyArmor.install(fixture.registry,adapter);
            assertFalse(fixture.registry.hasSystemClass(original.getClass()));assertTrue(fixture.registry.hasSystemClass(NativeEnemyArmor.class));
            assertSame(original.getQuery(),adapter.getQuery());assertSame(original.getGroup(),adapter.getGroup());
            assertThrows(IllegalStateException.class,()->NativeEnemyArmor.install(fixture.registry,new NativeEnemyArmor((store,target)->null)));
            lease.close();lease.close();
            assertFalse(fixture.registry.hasSystemClass(NativeEnemyArmor.class));
            var data=fixture.registry._internal_getData();boolean found=false;
            for(int i=0;i<data.getSystemSize();i++)if(data.getSystem(i)==original)found=true;
            assertTrue(found,"Restore the actual native owner, not a newly constructed substitute");
        }
    }
    static final class ExactDependent extends com.hypixel.hytale.component.system.tick.TickingSystem<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> {
        @Override public void tick(float dt,int index,com.hypixel.hytale.component.Store<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> store){}
        @Override public Set<com.hypixel.hytale.component.dependency.Dependency<com.hypixel.hytale.server.core.universe.world.storage.EntityStore>> getDependencies(){
            return Set.of(new com.hypixel.hytale.component.dependency.SystemDependency<>(com.hypixel.hytale.component.dependency.Order.AFTER,
                    com.hypixel.hytale.server.core.modules.entity.damage.DamageSystems.ArmorDamageReduction.class));
        }
    }
    @Test void anUnrecognizedPluginDependencyRejectsReplacementBeforeRemovingTheNativeOwner() throws Exception {
        try(var fixture=new RegistryFixture()){
            var original=new com.hypixel.hytale.server.core.modules.entity.damage.DamageSystems.ArmorDamageReduction();
            fixture.registry.registerSystem(original);fixture.registry.registerSystem(new ExactDependent());
            var failure=assertThrows(IllegalStateException.class,()->NativeEnemyArmor.install(fixture.registry,new NativeEnemyArmor((store,target)->null)));
            assertTrue(failure.getMessage().startsWith("NATIVE_ARMOR_EXACT_DEPENDENCY:"));
            assertTrue(fixture.registry.hasSystemClass(original.getClass()));assertFalse(fixture.registry.hasSystemClass(NativeEnemyArmor.class));
        }
    }
    static final class FaultRegistry extends com.hypixel.hytale.component.ComponentRegistry<com.hypixel.hytale.server.core.universe.world.storage.EntityStore>{
        boolean before,after;
        @Override public void registerSystem(com.hypixel.hytale.component.system.ISystem<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> system){
            if(system instanceof NativeEnemyArmor&&before){before=false;throw new IllegalStateException("fixture-before-insertion");}
            super.registerSystem(system);
            if(system instanceof NativeEnemyArmor&&after){after=false;throw new IllegalStateException("fixture-after-insertion");}
        }
    }
    abstract static class OrderedFilter implements com.hypixel.hytale.component.system.ISystem<com.hypixel.hytale.server.core.universe.world.storage.EntityStore>{
        @Override public com.hypixel.hytale.component.SystemGroup<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> getGroup(){
            return com.hypixel.hytale.server.core.modules.entity.damage.DamageModule.get().getFilterDamageGroup();
        }
    }
    static final class BeforeFilter extends OrderedFilter{}
    static final class BetweenFilter extends OrderedFilter{}
    static final class AfterFilter extends OrderedFilter{}
    static class ChunkOwner implements com.hypixel.hytale.component.system.ISystem<com.hypixel.hytale.server.core.universe.world.storage.ChunkStore>{}
    static final class ChunkAdapter extends ChunkOwner{}
    static final class ChunkPeer implements com.hypixel.hytale.component.system.ISystem<com.hypixel.hytale.server.core.universe.world.storage.ChunkStore>{}
    @Test void ungroupedChunkOwnerUsesTheSameReplacementAndRestorationPrimitive(){
        var registry=new com.hypixel.hytale.component.ComponentRegistry<com.hypixel.hytale.server.core.universe.world.storage.ChunkStore>();
        try{
            var original=new ChunkOwner();var peer=new ChunkPeer();registry.registerSystem(original);registry.registerSystem(peer);
            boolean first=registry._internal_getData().getSystem(0)==original;
            var adapter=new ChunkAdapter();
            var lease=com.inigmasgames.hytalerpg.combat.hytale.NativeSystemReplacement.install(registry,ChunkOwner.class,ChunkAdapter.class,adapter,"FIXTURE_CHUNK");
            var sorted=new ArrayList<Object>();var data=registry._internal_getData();
            for(int i=0;i<data.getSystemSize();i++)if(data.getSystem(i)==adapter||data.getSystem(i)==peer)sorted.add(data.getSystem(i));
            assertEquals(first?List.of(adapter,peer):List.of(peer,adapter),sorted);
            lease.close();sorted.clear();data=registry._internal_getData();
            for(int i=0;i<data.getSystemSize();i++)if(data.getSystem(i)==original||data.getSystem(i)==peer)sorted.add(data.getSystem(i));
            assertEquals(first?List.of(original,peer):List.of(peer,original),sorted);
        }finally{registry.shutdown();}
    }
    static List<String> filterOrder(RegistryFixture fixture){
        var result=new ArrayList<String>();var data=fixture.registry._internal_getData();
        for(int i=0;i<data.getSystemSize();i++){
            var system=data.getSystem(i);
            if(system instanceof com.hypixel.hytale.server.core.modules.entity.damage.DamageSystems.ArmorDamageReduction)result.add("armor");
            else if(system instanceof com.hypixel.hytale.server.core.modules.entity.damage.DamageSystems.ScaleOutgoingDamageFromEntityEffects)result.add("outgoing");
            else if(system instanceof OrderedFilter)result.add(system.getClass().getSimpleName());
        }
        return result;
    }
    static double orderedDamage(List<String> order){
        double damage=100;
        for(var step:order)damage=switch(step){
            case "armor"->Math.max(0,damage-13)*.8;
            case "outgoing"->damage*1.5+7;
            case "BeforeFilter"->damage+11;
            case "BetweenFilter"->Math.min(damage,90);
            case "AfterFilter"->damage*.7;
            default->throw new AssertionError(step);
        };
        return damage;
    }
    @Test void nativeFilterOrderSurvivesBothAdaptersTeardownAndReinstallation() throws Exception{
        try(var fixture=new RegistryFixture()){
            var armor=new com.hypixel.hytale.server.core.modules.entity.damage.DamageSystems.ArmorDamageReduction();
            var outgoing=new com.hypixel.hytale.server.core.modules.entity.damage.DamageSystems.ScaleOutgoingDamageFromEntityEffects();
            fixture.registry.registerSystem(new BeforeFilter());fixture.registry.registerSystem(armor);
            fixture.registry.registerSystem(new BetweenFilter());fixture.registry.registerSystem(outgoing);
            fixture.registry.registerSystem(new AfterFilter());
            var before=filterOrder(fixture);assertEquals(5,before.size());double expected=orderedDamage(before);
            for(int iteration=0;iteration<2;iteration++){
                var first=NativeEnemyArmor.install(fixture.registry,new NativeEnemyArmor((store,target)->null));
                assertEquals(before,filterOrder(fixture));
                var second=new com.inigmasgames.hytalerpg.execution.hytale.NativeEnemyOutgoingEffects().install(fixture.registry);
                assertEquals(before,filterOrder(fixture));assertEquals(expected,orderedDamage(filterOrder(fixture)));
                // Both closure orders must preserve native semantics.
                if(iteration==0){second.close();assertEquals(before,filterOrder(fixture));first.close();}
                else{first.close();assertEquals(before,filterOrder(fixture));second.close();}
                assertEquals(before,filterOrder(fixture));assertEquals(expected,orderedDamage(filterOrder(fixture)));
            }
        }
    }
    @Test void failuresBeforeAndAfterNativeRegistryInsertionRestoreExactlyOneOwner() throws Exception {
        for(boolean after:new boolean[]{false,true}){
            var registry=new FaultRegistry();
            try(var fixture=new RegistryFixture(registry)){
                var original=new com.hypixel.hytale.server.core.modules.entity.damage.DamageSystems.ArmorDamageReduction();
                registry.registerSystem(original);registry.before=!after;registry.after=after;
                assertThrows(IllegalStateException.class,()->NativeEnemyArmor.install(registry,new NativeEnemyArmor((store,target)->null)));
                assertTrue(registry.hasSystemClass(original.getClass()));assertFalse(registry.hasSystemClass(NativeEnemyArmor.class));
                var data=registry._internal_getData();int nativeOwners=0;
                for(int i=0;i<data.getSystemSize();i++)if(data.getSystem(i)==original)nativeOwners++;
                assertEquals(1,nativeOwners);
            }
        }
    }
}
