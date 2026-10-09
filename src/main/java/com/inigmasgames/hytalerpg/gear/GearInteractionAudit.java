package com.inigmasgames.hytalerpg.gear;

import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.InteractionManager;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.RootInteraction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.data.Collector;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.data.CollectorTag;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.DamageEntityInteraction;
import java.util.*;

/** Exhaustive native operation walk, with actual Replace variables. Unknown damaging routes fail closed. */
public final class GearInteractionAudit implements Collector {
    private int visited;
    private final Set<String> blockers=new TreeSet<>();
    private final Set<String> leaves=new TreeSet<>();
    public record Result(boolean supported,Set<String> managedLeaves,Set<String> blockers) {}
    public static Result inspect(InteractionType type,InteractionContext context,RootInteraction root) {
        var audit=new GearInteractionAudit();
        try { InteractionManager.walkChain(audit,type,context,Objects.requireNonNull(root)); }
        catch(RuntimeException exception) { audit.blockers.add("Native graph resolution: "+exception.getMessage()); }
        return new Result(audit.blockers.isEmpty(),Set.copyOf(audit.leaves),Set.copyOf(audit.blockers));
    }
    @Override public void start() {}
    @Override public void into(InteractionContext context,Interaction interaction) {}
    @Override public boolean collect(CollectorTag tag,InteractionContext context,Interaction interaction) {
        if(++visited>4096) throw new IllegalStateException("Gear graph operation limit");
        if(interaction instanceof DamageEntityInteraction) {
            if(interaction instanceof ManagedGearDamageInteraction || interaction instanceof ManagedCarrierDamageInteraction) leaves.add(interaction.getId());
            else blockers.add("Unmanaged native damage leaf: "+interaction.getId());
        }
        // Audit modern impacts too; legacy throws remain closed until their adapter is implemented.
        if(interaction instanceof ManagedGearProjectile || interaction instanceof ManagedCarrierProjectile) {
            var config=interaction instanceof ManagedGearProjectile projectile?projectile.getConfig():
                    ((ManagedCarrierProjectile)interaction).getConfig();
            for(var route:config.getInteractions().entrySet()) {
                InteractionManager.walkChain(this,route.getKey(),context,RootInteraction.getAssetMap().getAsset(route.getValue()));
            }
        } else if(interaction.getClass().getSimpleName().contains("Projectile")) blockers.add("Projectile adapter required: "+interaction.getId());
        // Explode_Generic applies fixed native EntityDamage directly. It is not a
        // DamageEntityInteraction leaf and otherwise evades the managed hit audit.
        if(interaction.getClass().getSimpleName().contains("Explode"))
            blockers.add("Unmanaged native explosion: "+interaction.getId());
        return false;
    }
    @Override public void outof() {}
    @Override public void finished() {}
}
