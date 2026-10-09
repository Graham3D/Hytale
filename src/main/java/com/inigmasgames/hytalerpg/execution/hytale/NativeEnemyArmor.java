package com.inigmasgames.hytalerpg.execution.hytale;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.server.core.entity.ItemUtils;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.container.EmptyItemContainer;
import com.hypixel.hytale.server.core.modules.entity.damage.*;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.inigmasgames.hytalerpg.combat.defense.DefenseView;
import com.inigmasgames.hytalerpg.difficulty.MonsterResistanceProfile;
import java.util.*;

/** Adapts the native armor boundary; it is never installed alongside that same native system. */
public final class NativeEnemyArmor extends DamageSystems.ArmorDamageReduction {
    public record Physical(int level, DefenseView.Contributions contributions, boolean immune) {
        public Physical {
            Objects.requireNonNull(contributions);
            if (level < 1 || level > 99) throw new IllegalArgumentException("ENEMY_DEFENSE_LEVEL");
        }
    }
    /** Read-only projection through the same native collector and D01 owner used by an incoming Physical hit. */
    public record PhysicalInspection(boolean immune,double managedProtection,double packetMultiplierProtection,
            double nativeFlatReduction,double totalDefenseRating,double effectiveDefenseRating){}
    public static PhysicalInspection inspectPhysical(Store<EntityStore> store,Ref<EntityStore> target,
            Physical projection){
        return inspectPhysical(store,target,DamageCause.PHYSICAL,projection);
    }
    public static PhysicalInspection inspectPhysical(Store<EntityStore> store,Ref<EntityStore> target,
            DamageCause cause,Physical projection){
        Objects.requireNonNull(store);Objects.requireNonNull(target);Objects.requireNonNull(projection);
        if(!store.isInThread()||!target.isValid()||target.getStore()!=store)
            throw new IllegalStateException("ENEMY_DEFENSE_INSPECT_WORLD_THREAD");
        if(!physical(cause))throw new IllegalArgumentException("ENEMY_DEFENSE_INSPECT_CAUSE");
        if(projection.immune())return new PhysicalInspection(true,0,1,0,0,0);
        var armor=store.getComponent(target,InventoryComponent.Armor.getComponentType());
        var inventory=armor==null?EmptyItemContainer.INSTANCE:armor.getInventory();
        var effects=store.getComponent(target,EffectControllerComponent.getComponentType());
        boolean penalties=ItemUtils.canApplyItemStackPenalties(target,store);
        var world=store.getExternalData().getWorld();
        var armorModifiers=getResistanceModifiers(world,inventory,penalties,null);
        var modifiers=effects==null?armorModifiers:getResistanceModifiers(world,inventory,penalties,effects);
        var baseline=armorModifiers.get(cause);
        float nativeProtection=baseline==null?0:baseline.multiplierModifier;
        if(!Float.isFinite(nativeProtection)||nativeProtection<0||nativeProtection>=1)
            throw new IllegalStateException("ENEMY_DEFENSE_NATIVE_PROTECTION_UNAVAILABLE");
        var view=DefenseView.resolve(projection.level(),nativeProtection,projection.contributions(),
                DefenseView.MANAGED_PROTECTION_CAP);
        projectPhysical(cause,modifiers,armorModifiers,projection);
        var packet=modifiers.get(cause);
        if(packet!=null&&packet.inheritedParentId!=null)
            throw new IllegalStateException("ENEMY_DEFENSE_NATIVE_INHERITANCE_UNAVAILABLE");
        return new PhysicalInspection(false,view.managedProtection(),packet==null?0:packet.multiplierModifier,
                packet==null?0:packet.flatModifier,view.totalRating(),view.effectiveRating());
    }
    @FunctionalInterface public interface Provider {
        Physical resolve(Store<EntityStore> store, Ref<EntityStore> target);
    }
    public record Elemental(MonsterResistanceProfile baseline, Map<MonsterResistanceProfile.Channel,Double> additions,
                            Set<MonsterResistanceProfile.Channel> immunities, double eligiblePenetration) {
        public Elemental {
            Objects.requireNonNull(baseline);additions=Map.copyOf(additions);immunities=Set.copyOf(immunities);
            new MonsterResistanceProfile(additions,immunities);
            if(!Double.isFinite(eligiblePenetration)||eligiblePenetration<0||eligiblePenetration>1)
                throw new IllegalArgumentException("ENEMY_ELEMENTAL_PENETRATION");
        }
        public boolean immune(MonsterResistanceProfile.Channel channel){
            return baseline.immune(channel)||immunities.stream().anyMatch(value->value.canonical()==channel.canonical());
        }
    }
    /** Exact read-only projection of the current native elemental collector for one installed cause. */
    public record ElementalInspection(MonsterResistanceProfile.Channel channel,boolean immune,
            double packetMultiplierProtection,double nativeFlatReduction){}
    public static ElementalInspection inspectElemental(Store<EntityStore> store,Ref<EntityStore> target,
            DamageCause cause,Elemental projection){
        Objects.requireNonNull(store);Objects.requireNonNull(target);Objects.requireNonNull(cause);Objects.requireNonNull(projection);
        if(!store.isInThread()||!target.isValid()||target.getStore()!=store)
            throw new IllegalStateException("ENEMY_ELEMENTAL_INSPECT_WORLD_THREAD");
        var channel=HytaleDifficultyCombat.channel(cause.getId());
        if(channel==null||cause.doesBypassResistances())
            throw new IllegalArgumentException("ENEMY_ELEMENTAL_INSPECT_CAUSE");
        if(projection.immune(channel))return new ElementalInspection(channel,true,1,0);
        var armor=store.getComponent(target,InventoryComponent.Armor.getComponentType());
        var inventory=armor==null?EmptyItemContainer.INSTANCE:armor.getInventory();
        var effects=store.getComponent(target,EffectControllerComponent.getComponentType());
        var modifiers=getResistanceModifiers(store.getExternalData().getWorld(),inventory,
                ItemUtils.canApplyItemStackPenalties(target,store),effects);
        projectElemental(cause,modifiers,projection);
        var packet=modifiers.get(cause);
        return new ElementalInspection(channel,false,packet==null?0:packet.multiplierModifier,
                packet==null?0:packet.flatModifier);
    }
    @FunctionalInterface public interface ElementalProvider {
        Elemental resolve(Store<EntityStore> store, Ref<EntityStore> target, Damage damage);
    }
    private final Provider provider;
    private final ElementalProvider elementalProvider;
    public NativeEnemyArmor(Provider provider) { this(provider,(store,target,damage)->null); }
    public NativeEnemyArmor(Provider provider,ElementalProvider elementalProvider) {
        this.provider = Objects.requireNonNull(provider);this.elementalProvider=Objects.requireNonNull(elementalProvider);
    }

    @Override public void handle(int index, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store,
                                 CommandBuffer<EntityStore> buffer, Damage damage) {
            try(var rpgTickSpan=com.inigmasgames.hytalerpg.diagnostics.NativeRpgTickMetrics.enter(store,com.inigmasgames.hytalerpg.diagnostics.NativeRpgTickMetrics.Phase.DAMAGE)){
        var cause = damage.getCause();
        if (damage.isCancelled() || cause == null || cause.doesBypassResistances()) {
            super.handle(index, chunk, store, buffer, damage);
            return;
        }
        var ref = chunk.getReferenceTo(index);
        if(!physical(cause)){
            var channel=HytaleDifficultyCombat.channel(cause.getId());
            var elemental=channel==null?null:elementalProvider.resolve(store,ref,damage);
            if(elemental==null){super.handle(index,chunk,store,buffer,damage);return;}
            if(elemental.immune(channel)){damage.setAmount(0);damage.setCancelled(true);return;}
            var armor=buffer.getComponent(ref,InventoryComponent.Armor.getComponentType());
            var modifiers=getResistanceModifiers(buffer.getExternalData().getWorld(),armor==null?EmptyItemContainer.INSTANCE:armor.getInventory(),
                    ItemUtils.canApplyItemStackPenalties(ref,buffer),chunk.getComponent(index,EffectControllerComponent.getComponentType()));
            projectElemental(cause,modifiers,elemental);
            damage.setAmount(applyNative(damage.getAmount(),cause,modifiers));return;
        }
        var projection = provider.resolve(store, ref);
        if (projection == null || !projection.immune() && projection.contributions().equals(DefenseView.Contributions.NONE)) {
            super.handle(index, chunk, store, buffer, damage);
            return;
        }
        if (projection.immune()) {
            damage.setAmount(0);
            damage.setCancelled(true);
            return;
        }
        var armor = buffer.getComponent(ref, InventoryComponent.Armor.getComponentType());
        var inventory = armor == null ? EmptyItemContainer.INSTANCE : armor.getInventory();
        var effects = chunk.getComponent(index, EffectControllerComponent.getComponentType());
        boolean penalties = ItemUtils.canApplyItemStackPenalties(ref, buffer);
        var world = buffer.getExternalData().getWorld();
        var armorModifiers = getResistanceModifiers(world, inventory, penalties, null);
        var modifiers = effects == null ? armorModifiers : getResistanceModifiers(world, inventory, penalties, effects);
        projectPhysical(cause, modifiers, armorModifiers, projection);
        damage.setAmount(applyNative(damage.getAmount(), cause, modifiers));
    
            }}

    public static boolean physical(DamageCause cause) {
        return cause != null && (cause.getId().equals("Physical") || cause.getId().equals("Projectile"));
    }

    /** Mutates only the collector's fresh, per-packet map. No asset, armor item or status is changed. */
    public static void projectPhysical(DamageCause cause, Map<DamageCause, ArmorResistanceModifiers> modifiers,
                                       Physical projection) {
        projectPhysical(cause, modifiers, modifiers, projection);
    }
    public static void projectPhysical(DamageCause cause, Map<DamageCause, ArmorResistanceModifiers> modifiers,
                                       Map<DamageCause, ArmorResistanceModifiers> armorModifiers, Physical projection) {
        if (!physical(cause) || cause.doesBypassResistances()) return;
        if (projection.immune()) throw new IllegalArgumentException("IMMUNITY_MUST_PRECEDE_DEFENSE");
        if (projection.contributions().equals(DefenseView.Contributions.NONE)) return;
        var current = modifiers.get(cause);
        var armor = armorModifiers.get(cause);
        // Installed Physical/Projectile causes have no inheritance. Do not invent a rating for a future chain.
        if (cause.getInherits() != null || current != null && current.inheritedParentId != null)
            throw new IllegalStateException("UNSUPPORTED_NATIVE_PHYSICAL_INHERITANCE:" + cause.getId());
        float nativeProtection = armor == null ? 0 : armor.multiplierModifier;
        if (!Float.isFinite(nativeProtection) || nativeProtection < 0)
            throw new IllegalStateException("UNSUPPORTED_NATIVE_PHYSICAL_PROTECTION:" + nativeProtection);
        // Full native protection is not a finite rating and must not be broken by ordinary Defense shred.
        if (nativeProtection >= 1) return;
        float nonmanaged = (current == null ? 0 : current.multiplierModifier) - nativeProtection;
        double protection = DefenseView.resolve(projection.level(), nativeProtection, projection.contributions(),
                DefenseView.MANAGED_PROTECTION_CAP).managedProtection();
        if (current == null) {
            current = new ArmorResistanceModifiers();
            modifiers.put(cause, current);
        }
        // Replace only the armor projection. Native effect bonuses/vulnerabilities keep their additive owner.
        current.multiplierModifier = (float) protection + nonmanaged;
    }

    /** One canonical elemental projection; supported native chains have flat reduction only at the leaf. */
    public static void projectElemental(DamageCause cause,Map<DamageCause,ArmorResistanceModifiers> modifiers,Elemental projection){
        var channel=HytaleDifficultyCombat.channel(cause.getId());
        if(channel==null||cause.doesBypassResistances())return;
        if(projection.immune(channel))throw new IllegalArgumentException("IMMUNITY_MUST_PRECEDE_RESISTANCE");
        var chain=new ArrayList<ArmorResistanceModifiers>();var current=modifiers.get(cause);double factor=1;
        while(current!=null){
            if(chain.contains(current)||chain.size()>=16)throw new IllegalStateException("NATIVE_RESISTANCE_INHERITANCE_CYCLE");
            if(!Float.isFinite(current.multiplierModifier)||current.multiplierModifier<0)
                throw new IllegalStateException("UNSUPPORTED_NATIVE_ELEMENTAL_VULNERABILITY:"+cause.getId());
            if(!chain.isEmpty()&&current.flatModifier!=0)
                throw new IllegalStateException("UNSUPPORTED_NATIVE_ELEMENTAL_PARENT_FLAT:"+cause.getId());
            chain.add(current);factor*=1-current.multiplierModifier;
            current=current.inheritedParentId==null?null:modifiers.get(current.inheritedParentId);
        }
        double raw=Math.max(0,1-factor);
        var combined=projection.baseline().withProviders(Map.of(channel,raw),projection.additions(),projection.immunities());
        double effective=Math.max(0,combined.effective(channel)-projection.eligiblePenetration());
        // No mutation until all compatibility and numeric checks have passed.
        var leaf=modifiers.get(cause);
        if(leaf==null){leaf=new ArmorResistanceModifiers();modifiers.put(cause,leaf);}
        for(var inherited:chain)inherited.multiplierModifier=0;
        leaf.multiplierModifier=(float)effective;
    }

    /** The installed native float operations/order, including independent flat values and inheritance. */
    public static float applyNative(float amount, DamageCause cause, Map<DamageCause, ArmorResistanceModifiers> modifiers) {
        if (cause.doesBypassResistances()) return amount;
        var current = modifiers.get(cause);
        if (current == null) return amount;
        var visited = Collections.newSetFromMap(new IdentityHashMap<ArmorResistanceModifiers, Boolean>());
        do {
            if (!visited.add(current)) throw new IllegalStateException("NATIVE_RESISTANCE_INHERITANCE_CYCLE");
            amount = Math.max(0f, amount - current.flatModifier);
            amount *= Math.max(0f, 1f - current.multiplierModifier);
            current = current.inheritedParentId == null ? null : modifiers.get(current.inheritedParentId);
        } while (current != null);
        return amount;
    }

    /** Keep the actual native instance, including its registration ownership, for rollback/teardown. */
    public static com.inigmasgames.hytalerpg.combat.hytale.NativeSystemReplacement install(ComponentRegistry<EntityStore> registry, NativeEnemyArmor adapter) {
        return com.inigmasgames.hytalerpg.combat.hytale.NativeSystemReplacement.install(registry,
                DamageSystems.ArmorDamageReduction.class,NativeEnemyArmor.class,adapter,"ARMOR");
    }
}
