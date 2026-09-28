package com.inigmasgames.hytalerpg.gear;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.dependency.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.inventory.*;
import com.hypixel.hytale.server.core.modules.entity.damage.*;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.interaction.event.InteractionChainStartEvent;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.inigmasgames.hytalerpg.combat.attribute.RpgAttribute;
import com.inigmasgames.hytalerpg.combat.hytale.DerivedStatEntityAdapter;
import com.inigmasgames.hytalerpg.progress.RpgLoadoutService;
import java.util.*;

/** Reads native equipped slots. Only the native armor owner consumes managed resistance. */
public final class HytaleGearEquipment {
    private final RpgLoadoutService players;
    private record Publication(Ref<EntityStore> actor,GearAffixRuntime.Effects effects) {}
    private final Map<UUID,Publication> published=new java.util.concurrent.ConcurrentHashMap<>();
    public GearAffixRuntime.Effects publishedEffects(UUID actor){var p=published.get(actor);return p==null||!players.ready(actor)||!p.actor().isValid()?GearAffixRuntime.Effects.NONE:p.effects();}
    private java.util.function.Consumer<com.hypixel.hytale.server.core.universe.world.World> lootTick=world->{};
    public void configureLootTick(java.util.function.Consumer<com.hypixel.hytale.server.core.universe.world.World> tick){lootTick=Objects.requireNonNull(tick);}
    public HytaleGearEquipment(RpgLoadoutService players) { this.players=players;players.addLoadoutMutationListener(published::remove); }
    public record View(int level,Map<RpgAttribute,Integer> baseline,List<GearInstance> equipped,GearRequirements.Validity validity) {}
    public GearAffixRuntime.Effects effects(Ref<EntityStore> actor,ComponentAccessor<EntityStore> accessor) {
        var v=findView(actor,accessor);if(v==null)return GearAffixRuntime.Effects.NONE;return GearAffixRuntime.effects(v.equipped().stream().filter(g->v.validity().valid().contains(g.identity())).toList());
    }
    public static Map<RpgAttribute,Integer> requirementAttributes(View view,UUID candidate) {
        return GearRequirements.resolve(view.level(),view.baseline(),view.equipped().stream()
                .filter(g->!g.identity().equals(candidate)).map(g->new GearRequirements.Equipped(g.identity(),g.requirements(),GearAffixRuntime.attributes(g))).toList()).permanentAttributes();
    }
    public double magicFind(Ref<EntityStore> actor,ComponentAccessor<EntityStore> accessor){var view=findView(actor,accessor);if(view==null)return 0;double gear=0;
        for(var item:view.equipped())if(!item.qaOnly()&&view.validity().valid().contains(item.identity()))for(var affix:item.affixes())if(affix.familyId().equals("WA-151"))gear+=affix.value()/100.;
        return GearMagicFind.snapshot(view.validity().permanentAttributes().getOrDefault(RpgAttribute.LUCK,10),gear);
    }
    /** Native readers must not hydrate or wait for persistence on the world thread. */
    View whenReady(UUID player,java.util.function.Supplier<View> read) {
        if(!players.ready(player)){published.remove(player);return null;}
        return read.get();
    }
    public View view(Ref<EntityStore> actor,ComponentAccessor<EntityStore> accessor) {
        var result=findView(actor,accessor);
        if(result==null)throw new IllegalStateException("PLAYER_PERSISTENCE_NOT_READY");
        return result;
    }
    private View findView(Ref<EntityStore> actor,ComponentAccessor<EntityStore> accessor) {
        var player=accessor.getComponent(actor,PlayerRef.getComponentType());
        if(player==null) throw new IllegalArgumentException("Player equipment required");
        return whenReady(player.getUuid(),()-> {
        var state=players.getPresentationView(player.getUuid()).state();
        var baseline=new EnumMap<RpgAttribute,Integer>(RpgAttribute.class);
        for(var attribute:RpgAttribute.values()) baseline.put(attribute,state.attributes.getOrDefault(attribute.name(),10));
        var items=new ArrayList<GearInstance>();
        collect(items,InventoryComponent.getItemInHand(accessor,actor),false);
        var utility=accessor.getComponent(actor,InventoryComponent.Utility.getComponentType());
        if(utility!=null) collect(items,utility.getActiveItem(),false);
        var armor=accessor.getComponent(actor,InventoryComponent.Armor.getComponentType());
        if(armor!=null) for(short slot=0;slot<armor.getInventory().getCapacity();slot++) collect(items,armor.getInventory().getItemStack(slot),true);
        var counts=new HashMap<UUID,Integer>();items.forEach(g->counts.merge(g.identity(),1,Integer::sum));
        items.removeIf(g->counts.get(g.identity())!=1);
        var candidates=items.stream().map(g->new GearRequirements.Equipped(g.identity(),g.requirements(),GearAffixRuntime.attributes(g))).toList();
        return new View(state.level,Map.copyOf(baseline),List.copyOf(items),GearRequirements.resolve(state.level,baseline,candidates));
        });
    }
    private static void collect(List<GearInstance> items,ItemStack stack,boolean armorSlot) {
        if(stack==null || stack.isEmpty() || stack.isBroken()) return;
        try { var gear=GearNativeItems.read(stack); if(gear!=null && GearAffixRuntime.supported(gear) && (gear.category()==GearCatalog.Category.ARMOR)==armorSlot) items.add(gear); }
        catch(RuntimeException malformed) { /* Inert neutral carrier. Native use is separately denied. */ }
    }
    public boolean canUse(ItemStack stack,Ref<EntityStore> actor,ComponentAccessor<EntityStore> accessor) {
        if(!GearNativeItems.managed(stack)) return true;
        try {
            var gear=GearNativeItems.read(stack);if(!GearAffixRuntime.supported(gear))return false;var view=view(actor,accessor);
            if(view.equipped().stream().filter(g->g.identity().equals(gear.identity())).count()>1) return false;
            var others=view.equipped().stream().filter(g->!g.identity().equals(gear.identity()))
                    .map(g->new GearRequirements.Equipped(g.identity(),g.requirements(),GearAffixRuntime.attributes(g))).toList();
            var attrs=GearRequirements.resolve(view.level(),view.baseline(),others).permanentAttributes();
            return !stack.isBroken() && view.validity().valid().contains(gear.identity()) && gear.requirements().failures(view.level(),attrs,gear.category()==GearCatalog.Category.ARMOR).isEmpty();
        } catch(RuntimeException invalid) { return false; }
    }
    /** A denied weapon must still be removable from the active hotbar slot. */
    static boolean validatesHeldItem(InteractionType type) {
        return type!=InteractionType.SwapFrom && type!=InteractionType.SwapTo;
    }
    public void project(Ref<EntityStore> actor,ComponentAccessor<EntityStore> accessor) {
        var view=findView(actor,accessor);if(view==null)return; double protection=0,health=0,mana=0,stamina=0;
        var manager=accessor.getComponent(actor,com.hypixel.hytale.server.core.modules.interaction.InteractionModule.get().getInteractionManagerComponent());
        if(manager!=null) for(var chain:List.copyOf(manager.getChains().values())) {
            var held=chain.getContext().getHeldItem();
            var proxy=chain.getContext().getEntity();
            if(ManagedGearProjectile.hasSnapshot(proxy,accessor))continue; // Released projectile snapshots are already committed.
            if(validatesHeldItem(chain.getType()) && GearNativeItems.managed(held) && !canUse(held,actor,accessor)) manager.cancelChains(chain);
        }
        for(var gear:view.equipped()) if(view.validity().valid().contains(gear.identity())) {
            var stats=gear.intrinsicStats();
            if(gear.category()==GearCatalog.Category.ARMOR) protection+=GearAffixRuntime.protection(gear);
            health+=stats.getOrDefault("health",0.0);mana+=stats.getOrDefault("mana",0.0);stamina+=stats.getOrDefault("stamina",0.0);
        }
        var nativeStats=accessor.getComponent(actor,EntityStatMap.getComponentType());
        var applied=GearAffixRuntime.effects(view.equipped().stream().filter(g->view.validity().valid().contains(g.identity())).toList());
        var owner=accessor.getComponent(actor,PlayerRef.getComponentType()).getUuid();
        if(!published.containsKey(owner)&&published.size()>=1024){published.entrySet().removeIf(e->!e.getValue().actor().isValid());if(published.size()>=1024)throw new IllegalStateException("GEAR_OWNER_CAPACITY");}
        published.put(owner,new Publication(actor,applied));
        var baseline=DERIVED.derive(view.baseline());var total=applied.derive(DERIVED,view.baseline());
        health=total.maxHealth()-baseline.maxHealth();mana=total.maxMana()-baseline.maxMana();stamina=total.maxStamina()-baseline.maxStamina();
        if(nativeStats!=null) DerivedStatEntityAdapter.applyGearCapacity(nativeStats,health,mana,stamina);
        var effects=accessor.getComponent(actor,EffectControllerComponent.getComponentType());
        if(effects==null) return;
        int tenth=(int)Math.round(Math.min(60,protection)*10);
        String expected=tenth==0?"":"RPG_Gear_Protection_"+tenth;
        for(int index:effects.getActiveEffectIndexes()) {
            var asset=EntityEffect.getAssetMap().getAsset(index);
            if(asset!=null && asset.getId().startsWith("RPG_Gear_Protection_") && !asset.getId().equals(expected))
                effects.removeEffect(actor,index,accessor);
        }
        if(tenth>0) {
            var effect=EntityEffect.getAssetMap().getAsset(expected);
            if(effect==null) throw new IllegalStateException("Managed native armor effect missing: "+expected);
            int index=EntityEffect.getAssetMap().getIndex(expected);
            var active=effects.getActiveEffects().get(index);
            if(active==null || active.getRemainingDuration()<1) effects.addEffect(actor,effect,accessor);
        }
    }
    public final class Tick extends EntityTickingSystem<EntityStore> {
        @Override public Query<EntityStore> getQuery() { return PlayerRef.getComponentType(); }
        @Override public void tick(float dt,int index,ArchetypeChunk<EntityStore> chunk,Store<EntityStore> store,CommandBuffer<EntityStore> buffer) {
            try(var span=com.inigmasgames.hytalerpg.diagnostics.NativeRpgTickMetrics.enter(store,com.inigmasgames.hytalerpg.diagnostics.NativeRpgTickMetrics.Phase.EXECUTION)) {
                tickEquipment(chunk.getReferenceTo(index),buffer);
                lootTick.accept(store.getExternalData().getWorld());
            }
        }
    }
    void tickEquipment(Ref<EntityStore> actor,ComponentAccessor<EntityStore> accessor) {
        try(var readyPathSpan=com.inigmasgames.hywind.readypath.ReadyPathProbe.span("RPG_EQUIPMENT_AND_HOVER_SCAN",com.inigmasgames.hywind.readypath.ReadyPathProbe.ENABLED ? accessor.getComponent(actor,PlayerRef.getComponentType()).getUuid() : null)) {
        project(actor,accessor);
        refreshHover(actor,accessor);

        }
    }
    private void refreshHover(Ref<EntityStore> actor,ComponentAccessor<EntityStore> accessor) {
        var view=findView(actor,accessor);if(view==null)return;
        var inventory=InventoryComponent.getCombined(accessor,actor,InventoryComponent.EVERYTHING);
        if(inventory==null)return;
        for(short slot=0;slot<inventory.getCapacity();slot++) {
            var stack=inventory.getItemStack(slot);if(!GearNativeItems.managed(stack)) continue;
            try {
                var gear=GearNativeItems.read(stack);
                var presented=GearNativeItems.present(stack,gear,view.level(),requirementAttributes(view,gear.identity()));
                if(!stack.equals(presented)) inventory.replaceItemStackInSlot(slot,stack,presented);
            } catch(RuntimeException malformed) { /* Keep the owned object inert for diagnostics; never delete it. */ }
        }
    }
    public final class Use extends EntityEventSystem<EntityStore,InteractionChainStartEvent> {
        public Use() { super(InteractionChainStartEvent.class); }
        @Override public Query<EntityStore> getQuery() { return PlayerRef.getComponentType(); }
        @Override public void handle(int i,ArchetypeChunk<EntityStore> chunk,Store<EntityStore> store,CommandBuffer<EntityStore> buffer,InteractionChainStartEvent event) {
            var proxy=event.getContext().getEntity();
            if(ManagedGearProjectile.hasSnapshot(proxy,buffer))return; // Proxy impact uses its frozen launch validation.
            if(!validatesHeldItem(event.getType()))return;
            if(!canUse(event.getContext().getHeldItem(),chunk.getReferenceTo(i),buffer)) event.setCancelled(true);
            else if(GearNativeItems.managed(event.getContext().getHeldItem())) {
                var gear=GearNativeItems.read(event.getContext().getHeldItem());
                if(gear.category()==GearCatalog.Category.HELD && !GearInteractionAudit.inspect(event.getType(),event.getContext(),event.getChain().getInitialRootInteraction()).supported())
                    event.setCancelled(true);
            }
        }
    }
    /** Refresh before native filtering, including respec/quick-move changes occurring earlier in this tick. */
    public final class BeforeArmor extends DamageEventSystem {
        @Override public Query<EntityStore> getQuery() { return PlayerRef.getComponentType(); }
        @Override public Set<Dependency<EntityStore>> getDependencies() {
            return Set.of(new SystemGroupDependency<>(Order.BEFORE,DamageModule.get().getFilterDamageGroup()));
        }
        @Override public void handle(int i,ArchetypeChunk<EntityStore> chunk,Store<EntityStore> store,CommandBuffer<EntityStore> buffer,Damage damage) {
            try(var span=com.inigmasgames.hytalerpg.diagnostics.NativeRpgTickMetrics.enter(store,com.inigmasgames.hytalerpg.diagnostics.NativeRpgTickMetrics.Phase.DAMAGE)) {
                project(chunk.getReferenceTo(i),buffer);
            }
        }
    }
    private static final com.inigmasgames.hytalerpg.combat.attribute.DerivedStatService DERIVED=derivedOwner();
    private static com.inigmasgames.hytalerpg.combat.attribute.DerivedStatService derivedOwner(){
        var p=com.inigmasgames.hytalerpg.combat.balance.CombatBalanceProfile.loadCanonical();
        return new com.inigmasgames.hytalerpg.combat.attribute.DerivedStatService(p,new com.inigmasgames.hytalerpg.combat.attribute.EffectiveAttributeService(p));
    }
}
