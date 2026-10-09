package com.inigmasgames.hytalerpg.gear;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.DoubleSupplier;

/** Post-application item-skill admission. The port queues canonical skill execution for a later world step. */
public final class ItemSkillTriggerRuntime {
    public interface Roll {double sample(String stableEventPurpose);}
    public record Child(UUID world,UUID actor,UUID target,String root,String skill,int rank,UUID item,
                        GearEffectSnapshot source) {
        public Child { if(rank!=1||source.forItem(item).empty())throw new IllegalArgumentException("INVALID_ITEM_SKILL_CHILD"); }
        public boolean noProc(){return true;}
        public boolean noCrit(){return false;}
        public boolean noLeech(){return true;}
        public boolean noLinkedPassives(){return true;}
        public double additionalMana(){return 0;}
    }
    public interface Port { void enqueue(Child child); }
    /** Accepted direct damage is the post-filter applied amount; absorption may leave HP loss at zero. */
    public record DirectHit(UUID world,UUID actor,UUID item,UUID target,String root,String contact,
                            GearEffectSnapshot validEquipment,boolean direct,boolean hostile,boolean noProc,
                            boolean reflected,boolean blocked,double acceptedDirectDamage,double procCoefficient,
                            boolean protectedTarget) {
        public DirectHit {Objects.requireNonNull(world);Objects.requireNonNull(actor);Objects.requireNonNull(item);
            Objects.requireNonNull(target);Objects.requireNonNull(root);Objects.requireNonNull(contact);
            Objects.requireNonNull(validEquipment);
            if(!Double.isFinite(acceptedDirectDamage)||acceptedDirectDamage<0||!Double.isFinite(procCoefficient)
                    ||procCoefficient<0||procCoefficient>1)throw new IllegalArgumentException("INVALID_ITEM_HIT_RECEIPT");}
    }
    /** Only the native post-block owner may set successful=true after guard-cost resolution. */
    public record Block(UUID world,UUID actor,UUID guardItem,String root,String contact,
                        GearEffectSnapshot validEquipment,boolean successful,boolean noProc,boolean reflected) {
        public Block {Objects.requireNonNull(world);Objects.requireNonNull(actor);Objects.requireNonNull(guardItem);
            Objects.requireNonNull(root);Objects.requireNonNull(contact);Objects.requireNonNull(validEquipment);}
    }
    private final Roll roll;
    private final Map<UUID,Double> boltLock=new HashMap<>(),healLock=new HashMap<>();
    private final Set<String> seen=new HashSet<>();
    private double sweepAt;
    public ItemSkillTriggerRuntime(Roll roll){this.roll=Objects.requireNonNull(roll);}
    public ItemSkillTriggerRuntime(DoubleSupplier roll){this(key->roll.getAsDouble());}
    private boolean chance(double percent,double coefficient,String key){
        if(percent<=0||coefficient<=0)return false;
        double u=roll.sample(key);if(!Double.isFinite(u)||u<0||u>=1)throw new IllegalArgumentException("INVALID_ITEM_SKILL_ROLL");
        return u<Math.min(1,percent*coefficient/100);
    }
    private boolean claim(UUID world,UUID actor,String root,String contact,String kind,double now){
        if(!Double.isFinite(now)||now<0)throw new IllegalArgumentException("INVALID_ITEM_SKILL_TIME");
        if(now>=sweepAt+30){seen.clear();boltLock.values().removeIf(until->until<=now);
            healLock.values().removeIf(until->until<=now);sweepAt=now;}
        if(boltLock.size()+healLock.size()>=8192)throw new IllegalStateException("ITEM_SKILL_LOCK_CAPACITY");
        if(seen.size()>=8192)throw new IllegalStateException("ITEM_SKILL_RECEIPT_CAPACITY");
        return seen.add(world+"/"+actor+"/"+root+"/"+(kind.equals("ATTACK")?"ROOT":contact)+"/"+kind);
    }
    /** Completed direct source-item hit, never a damage Filter or visual contact. */
    public void applied(DirectHit hit,double now,Port port){
        Objects.requireNonNull(port);
        if(!hit.direct()||!hit.hostile()||hit.noProc()||hit.reflected()||hit.blocked()
                ||hit.acceptedDirectDamage()<=0||hit.protectedTarget()||hit.validEquipment().forItem(hit.item()).empty())return;
        if(!claim(hit.world(),hit.actor(),hit.root(),hit.contact(),"ATTACK",now))return;
        if(boltLock.getOrDefault(hit.actor(),0d)>now)return;
        var local=hit.validEquipment().forItem(hit.item());
        for(var candidate:new String[][]{{"WA-145","fire_bolt"},{"WA-146","frost_bolt"}}){
            if(chance(local.value(candidate[0]),hit.procCoefficient(),hit.world()+"/"+hit.actor()+"/"+hit.root()+"/"+hit.contact()+"/"+candidate[0])){
                var child=new Child(hit.world(),hit.actor(),hit.target(),hit.root(),candidate[1],1,hit.item(),hit.validEquipment());
                port.enqueue(child);
                boltLock.put(hit.actor(),now+3);
                return;
            }
        }
    }
    /** Actual successful block; the child heals the blocking actor through the normal healing owner. */
    public void blocked(Block block,double now,Port port){
        Objects.requireNonNull(port);
        if(!block.successful()||block.noProc()||block.reflected()
                ||block.validEquipment().forItem(block.guardItem()).empty()
                ||healLock.getOrDefault(block.actor(),0d)>now)return;
        if(!claim(block.world(),block.actor(),block.root(),block.contact(),"BLOCK",now))return;
        var local=block.validEquipment().forItem(block.guardItem());
        if(chance(local.value("WA-147"),1,block.world()+"/"+block.actor()+"/"+block.root()+"/"+block.contact()+"/WA-147")){
            port.enqueue(new Child(block.world(),block.actor(),block.actor(),block.root(),"minor_heal",1,block.guardItem(),block.validEquipment()));
            healLock.put(block.actor(),now+8);
        }
    }
    public void clearWorld(UUID world){seen.removeIf(key->key.startsWith(world+"/"));}
    /** Detach receipt identities; the owner's short ICD survives an item swap or reconnect. */
    public void clearActor(UUID actor){seen.removeIf(key->key.contains("/"+actor+"/"));}
}
