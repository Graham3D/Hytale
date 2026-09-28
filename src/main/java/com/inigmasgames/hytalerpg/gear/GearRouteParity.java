package com.inigmasgames.hytalerpg.gear;

import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.entity.*;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.*;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.data.*;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.DamageEntityInteraction;
import com.hypixel.hytale.server.core.modules.projectile.interaction.ProjectileInteraction;
import java.util.*;

/** Installed-codec parity check, including impact graphs, targeted damage and charge coefficients. */
public final class GearRouteParity implements Collector {
    private final List<String> calculators=new ArrayList<>();
    private int visited;
    private GearRouteParity(){}
    public static int verify(Item nativeItem,Item managed){
        var expected=scan(nativeItem);var actual=scan(managed);
        if(!expected.equals(actual))throw new IllegalStateException("Native damage coefficient parity failed: "+nativeItem.getId()+" expected="+expected+" actual="+actual);
        return expected.size();
    }
    private static List<String> scan(Item item){
        var collector=new GearRouteParity();
        var context=InteractionContext.withoutEntity();context.setInteractionVarsGetter(ignored->item.getInteractionVars());
        for(var route:item.getInteractions().entrySet())InteractionManager.walkChain(collector,route.getKey(),context,RootInteraction.getAssetMap().getAsset(route.getValue()));
        Collections.sort(collector.calculators);return List.copyOf(collector.calculators);
    }
    @Override public void start(){}
    @Override public void into(InteractionContext context,Interaction interaction){}
    @Override public boolean collect(CollectorTag tag,InteractionContext context,Interaction interaction){
        if(++visited>20000)throw new IllegalStateException("Ranged parity graph limit");
        if(interaction instanceof DamageEntityInteraction damage){
            var encoded=DamageEntityInteraction.CODEC.encode(damage,new ExtraInfo()).asDocument();
            var values=new TreeMap<String,String>();
            for(String field:List.of("DamageCalculator","AngledDamage"))if(encoded.containsKey(field))values.put(field,encoded.get(field).toString());
            if(encoded.containsKey("TargetedDamage"))for(var target:encoded.getDocument("TargetedDamage").entrySet()){
                var calculator=target.getValue().asDocument().get("DamageCalculator");
                if(calculator!=null)values.put("target/"+target.getKey(),calculator.toString());
            }
            calculators.add(values.toString());
        }
        if(interaction instanceof ProjectileInteraction projectile)for(var route:projectile.getConfig().getInteractions().entrySet())
            InteractionManager.walkChain(this,route.getKey(),context,RootInteraction.getAssetMap().getAsset(route.getValue()));
        return false;
    }
    @Override public void outof(){}
    @Override public void finished(){}
}
