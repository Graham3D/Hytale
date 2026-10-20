package com.inigmasgames.hytalerpg.execution.hytale;

import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.InteractionManager;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.RootInteraction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.ChargingInteraction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.data.Collector;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.data.CollectorTag;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.DamageEntityInteraction;
import java.util.*;

/** Uses the installed engine's Replace/Charging walk, including the actual equipped item's variables.
 * Asset reachability only: this is never evidence that a player hit anything. */
public final class NativeBasicAttackPaths implements Collector {
    public enum Kind { NORMAL, CHARGED, AMBIGUOUS }
    private record Path(boolean charged,boolean afterDamage,Interaction operation) {}
    private final ArrayDeque<Path> stack=new ArrayDeque<>();
    private final IdentityHashMap<Interaction,Kind> damage=new IdentityHashMap<>();
    private Path pending=new Path(false,false,null);
    private int visited;
    public static NativeBasicAttackPaths resolve(InteractionContext context,RootInteraction root){
        var result=new NativeBasicAttackPaths();
        InteractionManager.walkChain(result,InteractionType.Primary,context,Objects.requireNonNull(root));
        return result;
    }
    @Override public void start() { if(visited!=0)throw new IllegalStateException("NESTED_NATIVE_WALK_UNSUPPORTED"); }
    @Override public void into(InteractionContext context,Interaction interaction){
        if(stack.size()>=64)throw new IllegalStateException("NATIVE_BASIC_PATH_DEPTH_LIMIT");
        stack.push(new Path(pending.charged(),pending.afterDamage()||interaction instanceof DamageEntityInteraction,interaction));
    }
    @Override public boolean collect(CollectorTag tag,InteractionContext context,Interaction interaction){
        if(++visited>4096)throw new IllegalStateException("NATIVE_BASIC_PATH_OPERATION_LIMIT");
        var parent=stack.peek();
        boolean charged=parent!=null&&parent.charged();
        // Shipped charge startup may fail its Stamina check and fall back to the ordinary swing.
        // Holding a button is not proof that the charged attack actually executed.
        if(parent!=null&&parent.operation() instanceof com.hypixel.hytale.server.core.modules.interaction.interaction.config.none.StatsConditionInteraction
                &&tag.equals(com.hypixel.hytale.server.core.modules.interaction.interaction.config.data.StringTag.of("Failed")))charged=false;
        if(tag instanceof ChargingInteraction.ChargingTag charge) {
            if(!Double.isFinite(charge.getSeconds())||charge.getSeconds()<0)throw new IllegalStateException("NATIVE_CHARGE_THRESHOLD_INVALID");
            // Sword has a second Charging operation: releasing it early returns to normal swings.
            // The final selected charge stage supersedes the outer entry threshold.
            charged=charge.getSeconds()>0;
        }
        pending=new Path(charged,parent!=null&&parent.afterDamage(),interaction);
        if(interaction instanceof DamageEntityInteraction&&!pending.afterDamage()){
            Kind kind=charged?Kind.CHARGED:Kind.NORMAL;
            damage.merge(interaction,kind,(a,b)->a==b?a:Kind.AMBIGUOUS);
        }
        return false;
    }
    @Override public void outof(){stack.pop();}
    @Override public void finished(){if(!stack.isEmpty())throw new IllegalStateException("UNBALANCED_NATIVE_WALK");}
    public Optional<Kind> classify(Object operation){
        Kind kind=damage.get(operation);return kind==null||kind==Kind.AMBIGUOUS?Optional.empty():Optional.of(kind);
    }
    public Map<String,String> audit(){
        var result=new TreeMap<String,String>();damage.forEach((interaction,kind)->result.put(interaction.getId(),kind.name()));return Map.copyOf(result);
    }
    public static Map<String,Object> auditInstalledMelee(){
        var result=new TreeMap<String,Object>();
        for(var itemRecord:com.inigmasgames.hytalerpg.combat.power.NativeItemPowerRegistry.loadCanonical().all()){
            if(!Set.of("SWORD","LONGSWORD","DAGGER","BATTLEAXE","MACE","SPEAR").contains(itemRecord.kind()))continue;
            var item=com.hypixel.hytale.server.core.asset.type.item.config.Item.getAssetMap().getAsset(itemRecord.itemId());
            var rootId=item==null?null:item.getInteractions().get(InteractionType.Primary);
            var root=rootId==null?null:RootInteraction.getAssetMap().getAsset(rootId);
            if(root==null)throw new IllegalStateException("NATIVE_BASIC_ROOT_MISSING:"+itemRecord.itemId());
            var context=InteractionContext.withoutEntity();context.setInteractionVarsGetter(ignored->item.getInteractionVars());
            var paths=resolve(context,root).audit();
            if(paths.isEmpty()||paths.containsValue("AMBIGUOUS"))throw new IllegalStateException("NATIVE_BASIC_PATH_UNCLASSIFIED:"+itemRecord.itemId()+":"+paths);
            result.put(itemRecord.itemId(),Map.of("root",rootId,"damagePaths",paths));
        }
        return Map.of("items",result,"connectedProof",false,"classification","RESOLVED_CHARGING_TAG_AND_ACTUAL_ITEM_REPLACE_VARIABLES");
    }
}
