package com.inigmasgames.hytalerpg.execution.hytale;

import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.InteractionManager;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.RootInteraction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.ChargingInteraction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.ChainingInteraction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.data.Collector;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.data.CollectorTag;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.DamageEntityInteraction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.LaunchProjectileInteraction;
import java.util.*;

/** Uses the installed engine's Replace/Charging walk, including the actual equipped item's variables.
 * Asset reachability only: this is never evidence that a player hit anything. */
public final class NativeBasicAttackPaths implements Collector {
    public enum Kind { NORMAL, CHARGED, AMBIGUOUS }
    private record Path(boolean charged,boolean afterDamage,Interaction operation,
            ChainingInteraction chain,int branch,boolean nestedChain) {}
    private final ArrayDeque<Path> stack=new ArrayDeque<>();
    private final IdentityHashMap<Interaction,Kind> damage=new IdentityHashMap<>();
    private final IdentityHashMap<DamageEntityInteraction,Integer> damageOccurrences=new IdentityHashMap<>();
    private final List<DamageEntityInteraction> orderedDamage=new ArrayList<>();
    private final IdentityHashMap<LaunchProjectileInteraction,Integer> projectileOccurrences=new IdentityHashMap<>();
    private final IdentityHashMap<Interaction,List<Path>> damagePaths=new IdentityHashMap<>();
    private Path pending=new Path(false,false,null,null,-1,false);
    private int visited;
    public static NativeBasicAttackPaths resolve(InteractionContext context,RootInteraction root){
        return resolve(context,root,InteractionType.Primary);
    }
    public static NativeBasicAttackPaths resolve(InteractionContext context,RootInteraction root,InteractionType type){
        var result=new NativeBasicAttackPaths();
        InteractionManager.walkChain(result,Objects.requireNonNull(type),context,Objects.requireNonNull(root));
        return result;
    }
    @Override public void start() { if(visited!=0)throw new IllegalStateException("NESTED_NATIVE_WALK_UNSUPPORTED"); }
    @Override public void into(InteractionContext context,Interaction interaction){
        if(stack.size()>=64)throw new IllegalStateException("NATIVE_BASIC_PATH_DEPTH_LIMIT");
        stack.push(new Path(pending.charged(),pending.afterDamage()
                ||interaction instanceof DamageEntityInteraction||interaction instanceof LaunchProjectileInteraction,
                interaction,pending.chain(),pending.branch(),pending.nestedChain()));
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
        ChainingInteraction chain=parent==null?null:parent.chain();
        int branch=parent==null?-1:parent.branch();
        boolean nestedChain=parent!=null&&parent.nestedChain();
        if(parent!=null&&parent.operation() instanceof ChainingInteraction selected){
            // Hytale's ChainingInteraction.tick0 jumps to exactly one indexed label per root.
            // Nested chains have no proof of exclusivity here, so leave them uncertified.
            if(!nestedChain&&chain==null&&tag instanceof ChainingInteraction.ChainingTag selectedBranch){
                chain=selected;branch=selectedBranch.getIndex();
            }else{chain=null;branch=-1;nestedChain=true;}
        }
        pending=new Path(charged,parent!=null&&parent.afterDamage(),interaction,chain,branch,nestedChain);
        if(interaction instanceof DamageEntityInteraction leaf){
            damageOccurrences.merge(leaf,1,Integer::sum);
            orderedDamage.add(leaf);
            damagePaths.computeIfAbsent(leaf,ignored->new ArrayList<>()).add(pending);
        }
        if(interaction instanceof LaunchProjectileInteraction leaf)projectileOccurrences.merge(leaf,1,Integer::sum);
        if((interaction instanceof DamageEntityInteraction||interaction instanceof LaunchProjectileInteraction)&&!pending.afterDamage()){
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
    /** Includes post-damage and repeated routes, which cannot be certified from classify() alone. */
    public Map<DamageEntityInteraction,Integer> damageOccurrences(){
        return Collections.unmodifiableMap(new IdentityHashMap<>(damageOccurrences));
    }
    public List<DamageEntityInteraction> orderedDamage(){return List.copyOf(orderedDamage);}
    /** A repeated leaf is safe as one strike only when every occurrence is in a different,
     * mutually exclusive branch of the same native Chaining operation. */
    public boolean exclusiveChainingDamage(DamageEntityInteraction leaf){
        var paths=damagePaths.get(leaf);
        if(paths==null||paths.size()<2||paths.getFirst().chain()==null)return false;
        var chain=paths.getFirst().chain();var seen=new HashSet<Integer>();
        for(var path:paths)if(path.chain()!=chain||path.branch()<0||!seen.add(path.branch()))return false;
        return true;
    }
    /** Native launch operations are independent strike producers. Counting them cannot certify a later hit. */
    public Map<LaunchProjectileInteraction,Integer> projectileOccurrences(){
        return Collections.unmodifiableMap(new IdentityHashMap<>(projectileOccurrences));
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
