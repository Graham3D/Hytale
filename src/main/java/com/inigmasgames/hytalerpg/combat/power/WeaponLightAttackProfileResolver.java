package com.inigmasgames.hytalerpg.combat.power;

import com.google.gson.*;
import com.inigmasgames.hytalerpg.combat.damage.WeaponDamageExecution;
import java.util.*;

/** Read/compile adapter for the first uncharged combo entry only. No native execution, geometry or state machine. */
public final class WeaponLightAttackProfileResolver {
    public interface Assets { JsonObject root(String id); JsonObject interaction(String id); }
    private record Span(double duration,double contact,List<WeaponLightAttackProfile.Component> components){}
    private final Assets assets;private final Map<String,String> vars;private int visits;
    public WeaponLightAttackProfileResolver(Assets assets,Map<String,String> vars){this.assets=assets;this.vars=Map.copyOf(vars);}
    public WeaponLightAttackProfile resolve(String weapon,String kind,String root,String revision){
        if(!Set.of("SWORD","LONGSWORD","DAGGER").contains(kind))throw unsupported("WEAPON_FAMILY");
        visits=0;var span=root(new JsonPrimitive(root),0);
        return new WeaponLightAttackProfile(weapon,kind,revision,span.duration,span.contact,span.components);
    }
    private Span root(JsonElement element,int depth){
        guard(depth);var json=element.isJsonPrimitive()?assets.root(element.getAsString()):element.getAsJsonObject();
        if(json==null||!json.has("Interactions"))throw unsupported("ROOT_MISSING");
        Span out=empty();for(var child:json.getAsJsonArray("Interactions"))out=sequence(out,operation(child,depth+1));return out;
    }
    private Span operation(JsonElement element,int depth){
        guard(depth);var json=element.isJsonPrimitive()?assets.interaction(element.getAsString()):element.getAsJsonObject();
        if(json==null)throw unsupported("INTERACTION_MISSING");
        String type=text(json,"Type","");double duration=number(json,"RunTime",0);
        if(duration<0||duration>5)throw unsupported("DURATION");
        Span here=empty();
        switch(type){
            case "Charging" -> {var next=json.getAsJsonObject("Next");if(next==null)throw unsupported("CHARGE_BRANCH");
                JsonElement zero=null;for(var entry:next.entrySet())if(Double.parseDouble(entry.getKey())==0)zero=entry.getValue();
                if(zero==null)throw unsupported("UNCHARGED_BRANCH");return operation(zero,depth+1);}
            case "Chaining" -> {var next=json.getAsJsonArray("Next");if(next==null||next.isEmpty())throw unsupported("COMBO_BRANCH");return operation(next.get(0),depth+1);}
            case "Replace" -> {String id=vars.get(text(json,"Var",""));
                if(id!=null)return root(new JsonPrimitive(id),depth+1);
                if(!json.has("DefaultValue"))throw unsupported("VARIABLE_MISSING");return root(json.get("DefaultValue"),depth+1);}
            case "Simple" -> { }
            case "Serial" -> {
                for(var child:json.getAsJsonArray("Interactions"))here=sequence(here,operation(child,depth+1));
            }
            case "Parallel" -> {
                double longest=0;Span damage=empty();
                for(var branch:json.getAsJsonArray("Interactions")){var part=root(branch,depth+1);longest=Math.max(longest,part.duration);
                    if(!part.components.isEmpty()){if(!damage.components.isEmpty())throw unsupported("PARALLEL_DAMAGE");damage=part;}}
                here=new Span(longest,damage.contact,damage.components);
            }
            case "Selector" -> {
                if(!json.has("HitEntity"))throw unsupported("SELECTOR_DAMAGE_MISSING");
                var hit=root(json.get("HitEntity"),depth+1);
                if(hit.components.isEmpty()||hit.duration!=0)throw unsupported("DELAYED_SELECTOR_DAMAGE");
                here=new Span(0,0,hit.components);
            }
            case "DamageEntity","RPG_ManagedWeaponFire" -> {
                for(String key:List.of("AngledDamage","TargetedDamage"))if(json.has(key)&&!json.get(key).isJsonNull()){
                    var entries=json.get(key).isJsonArray()?json.getAsJsonArray(key).asList():json.getAsJsonObject(key).asMap().values();
                    for(var entry:entries){var override=entry.getAsJsonObject();
                        // Native tick0 retains its base calculator when the angle/target override is null.
                        // Feedback-only overrides do not change the reference damage composition.
                        if(override.has("DamageCalculator")&&!override.get("DamageCalculator").isJsonNull())throw unsupported("CONDITIONAL_DAMAGE");
                    }
                }
                var calc=json.getAsJsonObject("DamageCalculator");
                if(calc==null||!text(calc,"Type","Absolute").equalsIgnoreCase("Absolute")||number(calc,"SequentialModifierStep",0)!=0)
                    throw unsupported("CALCULATOR");
                double variance=number(calc,"RandomPercentageModifier",0);if(variance<0||variance>1)throw unsupported("VARIANCE");
                var components=new ArrayList<WeaponLightAttackProfile.Component>();
                for(var entry:calc.getAsJsonObject("BaseDamage").entrySet()){
                    double base=entry.getValue().getAsDouble();String channel=entry.getKey().equalsIgnoreCase("Ice")?"COLD":entry.getKey().toUpperCase(Locale.ROOT);
                    components.add(new WeaponLightAttackProfile.Component("weapon/"+channel,channel,base*(1-variance),base*(1+variance),"native-calculator",
                        WeaponDamageExecution.Provenance.WEAPON,true));
                }
                here=new Span(0,0,List.copyOf(components));
            }
            default -> throw unsupported("OPERATION_"+type.replaceAll("[^A-Za-z0-9_]",""));
        }
        here=new Span(here.duration+duration,here.contact,here.components);
        if(json.has("Next")&&!json.get("Next").isJsonNull()){
            if(type.equals("DamageEntity")||type.equals("RPG_ManagedWeaponFire"))feedbackTail(json.get("Next"),depth+1);
            else here=sequence(here,operation(json.get("Next"),depth+1));
        }
        return here;
    }
    /** Shipped DamageEntityParent's immediate recipient feedback is not source damage or attack-cycle time.
     * Validate this narrow tail rather than cloning potion cancellation or silently ignoring arbitrary effects. */
    private void feedbackTail(JsonElement element,int depth){
        guard(depth);var json=element.isJsonPrimitive()?assets.interaction(element.getAsString()):element.getAsJsonObject();
        if(json==null||number(json,"RunTime",0)!=0)throw unsupported("DAMAGE_TAIL");
        switch(text(json,"Type","")){
            case "Serial" -> {for(var child:json.getAsJsonArray("Interactions"))feedbackTail(child,depth+1);}
            case "ApplyEffect" -> {
                if(!text(json,"EffectId","").equals("Red_Flash")||!text(json,"Entity","").equals("Target"))throw unsupported("DAMAGE_TAIL_EFFECT");
            }
            case "ClearEntityEffect" -> {
                if(!text(json,"Entity","").equals("Target")||!Set.of("Potion_Health_Regen_Lesser","Potion_Health_Regen_Small",
                    "Potion_Health_Regen","Potion_Health_Regen_Greater","Potion_Health_Regen_Large","Potion_Stamina_Regen")
                    .contains(text(json,"EntityEffectId","")))throw unsupported("DAMAGE_TAIL_CLEAR");
            }
            default -> throw unsupported("DAMAGE_TAIL_OPERATION");
        }
        if(json.has("Next")&&!json.get("Next").isJsonNull())feedbackTail(json.get("Next"),depth+1);
    }
    private Span sequence(Span a,Span b){
        if(!a.components.isEmpty()&&!b.components.isEmpty())throw unsupported("MULTIPLE_DAMAGE_LEAVES");
        return new Span(a.duration+b.duration,a.components.isEmpty()?a.duration+b.contact:a.contact,a.components.isEmpty()?b.components:a.components);
    }
    private Span empty(){return new Span(0,0,List.of());}
    private void guard(int depth){if(depth>48||++visits>256)throw unsupported("GRAPH_BUDGET");}
    private static double number(JsonObject j,String k,double fallback){return j.has(k)?j.get(k).getAsDouble():fallback;}
    private static String text(JsonObject j,String k,String fallback){return j.has(k)?j.get(k).getAsString():fallback;}
    private static IllegalArgumentException unsupported(String reason){return new IllegalArgumentException("UNSUPPORTED_LIGHT_ATTACK_"+reason);}
}
