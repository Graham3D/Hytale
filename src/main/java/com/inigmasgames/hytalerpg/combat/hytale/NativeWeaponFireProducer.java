package com.inigmasgames.hytalerpg.combat.hytale;

import com.hypixel.hytale.server.core.entity.*;
import com.hypixel.hytale.server.core.meta.MetaKey;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.modules.projectile.component.ImpactModifiers;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.inigmasgames.hytalerpg.combat.damage.*;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiConsumer;

/** Authoritative NORMALIZED_FIRE_V1 producer, NOT reconstruction of an earlier native roll.
 * Root context owns the retained ledger; no global time cache and no retained command buffer. */
public final class NativeWeaponFireProducer implements ManagedWeaponFireInteraction.Router {
    public static Map<String,String> auditInstalledBinding(){
        var item=com.hypixel.hytale.server.core.asset.type.item.config.Item.getAssetMap().getAsset("Weapon_Longsword_Flame");
        if(item==null)throw new IllegalStateException("MANAGED_FIRE_ITEM_MISSING");
        var context=InteractionContext.withoutEntity();context.setInteractionVarsGetter(ignored->item.getInteractionVars());
        var root=com.hypixel.hytale.server.core.modules.interaction.interaction.config.RootInteraction.getAssetMap()
                .getAsset(item.getInteractions().get(com.hypixel.hytale.protocol.InteractionType.Primary));
        var paths=com.inigmasgames.hytalerpg.execution.hytale.NativeBasicAttackPaths.resolve(context,root).audit();
        var managed=new TreeMap<String,String>();
        paths.forEach((id,kind)->{if(Interaction.getAssetMap().getAsset(id) instanceof ManagedWeaponFireInteraction)managed.put(id,kind);});
        if(managed.size()!=4)throw new IllegalStateException("MANAGED_FIRE_PRIMARY_BINDING_COUNT:"+managed.size());
        return Map.copyOf(managed);
    }
    private static final MetaKey<Owner> OWNER=Interaction.CONTEXT_META_REGISTRY.registerMetaObject(
            ignored->null,false,"InigmasGames:ManagedWeaponFireV1",null);
    private record Owner(UUID actor,UUID world,String root,WeaponExecutionLedger ledger){}
    private final BiConsumer<InteractionContext,WeaponFireDecision> route;
    private final java.util.function.ToDoubleFunction<InteractionContext> offensiveFactor;
    public NativeWeaponFireProducer(BiConsumer<InteractionContext,WeaponFireDecision> route,java.util.function.ToDoubleFunction<InteractionContext> offensiveFactor){this.route=Objects.requireNonNull(route);this.offensiveFactor=Objects.requireNonNull(offensiveFactor);}
    @Override public float route(InteractionContext context,String leaf,String attribute,float base,float variance){
        var buffer=context.getCommandBuffer();var actor=context.getOwningEntity();var chain=context.getChain();
        if(buffer==null||actor==null||!actor.isValid()||chain==null||context.getEntry()==null
                ||context.getEntry().isUseSimulationState())return Float.NaN;
        var player=buffer.getComponent(actor,PlayerRef.getComponentType());
        var item=context.getOriginalItemType();
        // AQ coverage is opt-in via authored item leaves, never inferred from a Fire packet/name.
        if(player==null||item==null||item.getWeapon()==null||!actor.equals(context.getEntity())
                ||!item.getId().equals("Weapon_Longsword_Flame")||chain.getType()!=com.hypixel.hytale.protocol.InteractionType.Primary)return Float.NaN;
        var root=context.getInteractionManager().getChains().get(chain.getChainId());
        if(root==null)return Float.NaN;
        var meta=root.getContext().getMetaStore();var owner=meta.getIfPresentMetaObject(OWNER);
        if(owner==null){var id=UUID.randomUUID().toString();owner=new Owner(player.getUuid(),player.getWorldUuid(),id,
                new WeaponExecutionLedger(player.getUuid(),id));meta.putMetaObject(OWNER,owner);}
        if(!owner.actor.equals(player.getUuid())||!owner.world.equals(player.getWorldUuid()))return Float.NaN;
        // The parent selection entry is shared by cleave victim forks; victim subIndex is excluded.
        var fork=chain.getForkedChainId();
        if(fork!=null&&fork.forkedId!=null)return Float.NaN; // nested/derived branch has no coverage yet
        String authored=(fork==null?context.getEntry().getIndex():fork.entryIndex)+":"+leaf;
        var identity=new WeaponDamageExecution.Identity(owner.world,owner.actor,owner.root,authored,authored);
        var impact=ImpactModifiers.resolve(context,context.getEntity(),buffer);
        if(impact!=null&&impact.getElementalCauseId()!=null&&!impact.getElementalCauseId().equals("Fire"))return Float.NaN;
        double factor=(attribute==null||impact==null?1:impact.getAttribute(attribute,1))*offensiveFactor.applyAsDouble(context);
        if(!Double.isFinite(factor)||factor<=0)return Float.NaN;
        final double sourceFactor=factor;
        var decision=owner.ledger.produce(identity,()->{
            double sample=variance==0?0:ThreadLocalRandom.current().nextDouble(-variance,variance);
            double resolved=base*(1+sample)*sourceFactor;
            return new WeaponDamageExecution(identity,item.getId(),WeaponDamageExecution.Delivery.NATIVE_MELEE,false,true,false,
                    List.of(new WeaponDamageExecution.Component("native-fire-v1","FIRE",resolved,WeaponDamageExecution.Provenance.WEAPON,true,true)));
        });
        ManagedWeaponFireInteraction.afterNativeResolved(()->route.accept(context,decision));
        // Native leaf performs source-attribute multiplication below calculateDamage. Supply its
        // pre-attribute value so that direct damage gets it once, while the envelope is source-scaled.
        return (float)(decision.execution().sourceFire()/sourceFactor);
    }
}
