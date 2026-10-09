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
    @FunctionalInterface public interface DefenseContributions {
        com.inigmasgames.hytalerpg.combat.defense.DefenseView.Contributions read(
                Ref<EntityStore> actor,ComponentAccessor<EntityStore> accessor);
    }
    private DefenseContributions defenseContributions=(actor,accessor)->com.inigmasgames.hytalerpg.combat.defense.DefenseView.Contributions.NONE;
    public void configureDefense(DefenseContributions provider){defenseContributions=Objects.requireNonNull(provider);}
    private final RpgLoadoutService players;
    private record Publication(Ref<EntityStore> actor,List<GearInstance> valid,GearAffixRuntime.Effects effects) {}
    private final Map<UUID,Publication> published=new java.util.concurrent.ConcurrentHashMap<>();
    public GearAffixRuntime.Effects publishedEffects(UUID actor){var p=published.get(actor);return p==null||!players.ready(actor)||!p.actor().isValid()?GearAffixRuntime.Effects.NONE:p.effects();}
    private java.util.function.Consumer<com.hypixel.hytale.server.core.universe.world.World> lootTick=world->{};
    public void configureLootTick(java.util.function.Consumer<com.hypixel.hytale.server.core.universe.world.World> tick){lootTick=Objects.requireNonNull(tick);}
    public HytaleGearEquipment(RpgLoadoutService players) { this.players=players;players.addLoadoutMutationListener(published::remove); }
    public record View(int level,Map<RpgAttribute,Integer> baseline,List<GearInstance> equipped,GearRequirements.Validity validity) {}
    public GearAffixRuntime.Effects effects(Ref<EntityStore> actor,ComponentAccessor<EntityStore> accessor) {
        var v=findView(actor,accessor);if(v==null)return GearAffixRuntime.Effects.NONE;
        return resolveEffects(actor,accessor,v);
    }
    /** Attribute-derived offense before the envelope adds flat gear critical modifiers once. */
    public com.inigmasgames.hytalerpg.combat.attribute.DerivedStats attributeDerived(
            Ref<EntityStore> actor,ComponentAccessor<EntityStore> accessor) {
        var view=view(actor,accessor);
        var effects=resolveEffects(actor,accessor,view);
        return DERIVED.derive(effects.raw(view.baseline()),effects.health(),effects.stamina(),effects.mana());
    }
    private GearAffixRuntime.Effects resolveEffects(Ref<EntityStore> actor,ComponentAccessor<EntityStore> accessor,View view) {
        var owner=accessor.getComponent(actor,PlayerRef.getComponentType()).getUuid();
        var valid=view.equipped().stream().filter(g->view.validity().valid().contains(g.identity())).toList();
        var previous=published.get(owner);
        if(previous!=null&&previous.actor()==actor&&previous.valid().equals(valid)){
            GearQaTrace.equipment(owner,previous.effects().snapshot(),view.equipped(),view.validity().valid());
            return previous.effects();
        }
        if(previous==null&&published.size()>=1024){published.entrySet().removeIf(e->!e.getValue().actor().isValid());
            if(published.size()>=1024)throw new IllegalStateException("GEAR_OWNER_CAPACITY");}
        var effects=GearAffixRuntime.effects(valid);
        published.put(owner,new Publication(actor,valid,effects));
        GearQaTrace.equipment(owner,effects.snapshot(),view.equipped(),view.validity().valid());
        return effects;
    }
    public static Map<RpgAttribute,Integer> requirementAttributes(View view,UUID candidate) {
        return GearRequirements.resolve(view.level(),view.baseline(),view.equipped().stream()
                .filter(g->!g.identity().equals(candidate)).map(g->new GearRequirements.Equipped(g.identity(),g.requirements(),GearAffixRuntime.attributes(g))).toList()).permanentAttributes();
    }
    public record MagicFindBreakdown(double luck, double equippedGear) {
        public double total() { return luck + equippedGear; }
    }
    public MagicFindBreakdown magicFindBreakdown(Ref<EntityStore> actor,ComponentAccessor<EntityStore> accessor){
        var view=findView(actor,accessor);if(view==null)return new MagicFindBreakdown(0,0);
        return magicFindBreakdown(view.validity().permanentAttributes().getOrDefault(RpgAttribute.LUCK,10),
                view.equipped().stream().filter(item->view.validity().valid().contains(item.identity())).toList());
    }
    /** Same accepted-equipment owner for reward attribution, UI and offline qualification. */
    public static MagicFindBreakdown magicFindBreakdown(int rawLuck,Collection<GearInstance> validEquipment){
        double gear=validEquipment.stream()
                .flatMap(item->item.affixes().stream()).filter(a->a.familyId().equals("WA-151"))
                .mapToDouble(a->a.value()/100.0).sum();
        double luck=GearMagicFind.snapshot(rawLuck,0);
        return new MagicFindBreakdown(luck,GearMagicFind.snapshot(rawLuck,gear)-luck);
    }
    public double magicFind(Ref<EntityStore> actor,ComponentAccessor<EntityStore> accessor){return magicFindBreakdown(actor,accessor).total();}
    /** Reward capture reads the admitted equipped snapshot once, including authored QA gear. */
    public static double goldFind(GearEffectSnapshot admitted) {
        return admitted.sources(GearEffectSnapshot.Operator.GOLD_FIND).stream()
                .mapToDouble(GearEffectSnapshot.Source::value).sum();
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
        var slots=new ArrayList<GearEquipmentResolution.Candidate>();
        collect(slots,InventoryComponent.getItemInHand(accessor,actor),GearCatalog.Slot.HELD);
        var utility=accessor.getComponent(actor,InventoryComponent.Utility.getComponentType());
        if(utility!=null) collect(slots,utility.getActiveItem(),GearCatalog.Slot.HELD);
        var armor=accessor.getComponent(actor,InventoryComponent.Armor.getComponentType());
        if(armor!=null) for(var slot:com.hypixel.hytale.protocol.ItemArmorSlot.values())
            if(slot.getValue()<armor.getInventory().getCapacity())collect(slots,
                    armor.getInventory().getItemStack((short)slot.getValue()),GearCatalog.Slot.valueOf(slot.name().toUpperCase(Locale.ROOT)));
        var resolved=GearEquipmentResolution.resolve(state.level,baseline,slots);
        return new View(state.level,Map.copyOf(baseline),resolved.candidates(),resolved.validity());
        });
    }
    private static final GearCatalog EQUIPMENT_CATALOG=GearCatalog.load();
    private static void collect(List<GearEquipmentResolution.Candidate> items,ItemStack stack,GearCatalog.Slot slot) {
        if(stack==null || stack.isEmpty()) return;
        try { var gear=GearNativeItems.read(stack); if(gear!=null)items.add(new GearEquipmentResolution.Candidate(
                gear,true,!stack.isBroken(),EQUIPMENT_CATALOG.base(gear.baseId()).slot()==slot)); }
        catch(RuntimeException malformed) { /* Inert neutral carrier. Native use is separately denied. */ }
    }
    public boolean canUse(ItemStack stack,Ref<EntityStore> actor,ComponentAccessor<EntityStore> accessor) {
        if(!GearNativeItems.managed(stack)) return true;
        try {
            if(com.hypixel.hytale.server.core.asset.type.item.config.Item.getAssetMap().getAsset(stack.getItemId())==null)return false;
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
            if(ManagedGearProjectile.hasSnapshot(proxy,accessor)||ManagedCarrierProjectile.hasSnapshot(proxy,accessor))continue; // Released projectile snapshots are already committed.
            if(validatesHeldItem(chain.getType()) && GearNativeItems.managed(held) && !canUse(held,actor,accessor)) manager.cancelChains(chain);
        }
        for(var gear:view.equipped()) if(view.validity().valid().contains(gear.identity())) {
            var stats=gear.intrinsicStats();
            if(gear.category()==GearCatalog.Category.ARMOR) protection+=GearAffixRuntime.protection(gear);
            health+=stats.getOrDefault("health",0.0);mana+=stats.getOrDefault("mana",0.0);stamina+=stats.getOrDefault("stamina",0.0);
        }
        var nativeStats=accessor.getComponent(actor,EntityStatMap.getComponentType());
        var applied=resolveEffects(actor,accessor,view);
        var baseline=DERIVED.derive(view.baseline());var total=applied.derive(DERIVED,view.baseline());
        health=total.maxHealth()-baseline.maxHealth();mana=total.maxMana()-baseline.maxMana();stamina=total.maxStamina()-baseline.maxStamina();
        if(nativeStats!=null) DerivedStatEntityAdapter.applyGearCapacity(nativeStats,health,mana,stamina);
        var effects=accessor.getComponent(actor,EffectControllerComponent.getComponentType());
        if(effects==null) return;
        // One managed physical projection owns armor, shield and global Defense. The inverse
        // rating bridge preserves unaffixed armor rather than layering a second reduction.
        var extra=defenseContributions.read(actor,accessor);
        int tenth=nativeProtectionTenth(applied.snapshot(),view.level(),extra);
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
    /** The actual native effect selection, shared by projection and offline codec qualification. */
    public static int nativeProtectionTenth(GearEffectSnapshot admitted,int level) {
        return nativeProtectionTenth(admitted,level,com.inigmasgames.hytalerpg.combat.defense.DefenseView.Contributions.NONE);
    }
    public static int nativeProtectionTenth(GearEffectSnapshot admitted,int level,
            com.inigmasgames.hytalerpg.combat.defense.DefenseView.Contributions extra) {
        double protection=admitted.items().stream().filter(item->item.category()==GearCatalog.Category.ARMOR)
                .mapToDouble(GearAffixRuntime::protection).sum();
        var defense=GearDefenseEffects.resolve(admitted,level,Math.min(60,protection)/100.0,
                extra.otherRating()+extra.shieldRating(),extra.winningDefenseBreakFraction());
        double k=com.inigmasgames.hytalerpg.combat.defense.DefenseView.scale(level);
        double total=Math.max(0,defense.totalRating()*(1+extra.globalDefenseIncreased()));
        double effective=total*(1-extra.winningDefenseBreakFraction());
        return (int)Math.round(Math.clamp(effective/(k+effective),0,.60)*1000);
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
        var snapshot=resolveEffects(actor,accessor,view).snapshot();
        var inventory=InventoryComponent.getCombined(accessor,actor,InventoryComponent.EVERYTHING);
        if(inventory==null)return;
        UUID activeMainhand=null;
        try { var held=GearNativeItems.read(InventoryComponent.getItemInHand(accessor,actor));
            if(held!=null && !snapshot.forItem(held.identity()).empty()) activeMainhand=held.identity();
        } catch(RuntimeException invalid) { /* Invalid held gear cannot authorize a twin Utility carrier. */ }
        for(short slot=0;slot<inventory.getCapacity();slot++) {
            var stack=inventory.getItemStack(slot);if(!GearNativeItems.managed(stack)) continue;
            try {
                var gear=GearNativeItems.read(stack);
                var action=GearNativeItems.actionVariant(stack,snapshot,activeMainhand);
                var presented=GearNativeItems.present(action,gear,view.level(),requirementAttributes(view,gear.identity()));
                if(!stack.equals(presented) && !inventory.replaceItemStackInSlot(slot,stack,presented).succeeded())
                    System.err.println("GEAR_ACTION_CAS_RETRY slot="+slot+" carrier="+stack.getItemId());
            } catch(RuntimeException malformed) {
                System.err.println("GEAR_ACTION_OR_PRESENTATION_FAILED slot="+slot+" carrier="+stack.getItemId()+" cause="+malformed);
                /* Keep the owned object inert for diagnostics; never delete it. */
            }
        }
    }
    public final class Use extends EntityEventSystem<EntityStore,InteractionChainStartEvent> {
        public Use() { super(InteractionChainStartEvent.class); }
        @Override public Query<EntityStore> getQuery() { return PlayerRef.getComponentType(); }
        @Override public void handle(int i,ArchetypeChunk<EntityStore> chunk,Store<EntityStore> store,CommandBuffer<EntityStore> buffer,InteractionChainStartEvent event) {
            var proxy=event.getContext().getEntity();
            if(ManagedGearProjectile.hasSnapshot(proxy,buffer)||ManagedCarrierProjectile.hasSnapshot(proxy,buffer))return; // Proxy impact uses its frozen launch validation.
            if(!validatesHeldItem(event.getType()))return;
            if(!canUse(event.getContext().getHeldItem(),chunk.getReferenceTo(i),buffer)) event.setCancelled(true);
            else if(GearNativeItems.managed(event.getContext().getHeldItem())) {
                var gear=GearNativeItems.read(event.getContext().getHeldItem());
                 if(event.getType()==InteractionType.Primary && gear.category()==GearCatalog.Category.HELD) {
                     var accepted=effects(chunk.getReferenceTo(i),buffer).snapshot();
                     var local=accepted.forItem(gear.identity());
                     if(!local.empty() && (local.value("WA-008")>0 || local.value("WA-014")>0
                             || local.value("WA-141")>0)) {
                         var profile=NativeGearActionProfiles.primary(accepted,gear.identity());
                         if(!profile.rootId().equals(event.getRootInteractionId())) {event.setCancelled(true);return;}
                     }
                 }
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
