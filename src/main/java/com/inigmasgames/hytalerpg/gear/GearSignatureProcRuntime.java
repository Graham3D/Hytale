package com.inigmasgames.hytalerpg.gear;

import java.util.*;
import java.util.function.DoubleSupplier;

/** World-thread ledger for item attack procs. The caller supplies actual native contact and HP facts. */
public final class GearSignatureProcRuntime {
    public enum Kind { COMMON, ELITE, BOSS, PLAYER }
    public enum ChildKind { CRUSHING, BARBED, RETRIBUTION, KILL_BURST, CULL }
    public record Child(ChildKind kind,UUID world,UUID owner,UUID target,String root,String event,
                        Map<GearCombatEffects.Channel,Double> channels) {
        public Child { Objects.requireNonNull(kind);Objects.requireNonNull(world);Objects.requireNonNull(owner);
            Objects.requireNonNull(target);Objects.requireNonNull(root);Objects.requireNonNull(event);channels=Map.copyOf(channels);
            if(root.isBlank()||event.isBlank())throw new IllegalArgumentException("INVALID_SIGNATURE_ID");
            if(channels.isEmpty()||channels.values().stream().anyMatch(value->!Double.isFinite(value)||value<=0||value>Float.MAX_VALUE))
                throw new IllegalArgumentException("INVALID_SIGNATURE_CHILD"); }
        public boolean noProc(){return true;}
        public boolean noCrit(){return true;}
        public boolean noLeech(){return true;}
    }
    public record Contact(UUID world,UUID owner,UUID item,UUID target,String root,String contact,
                          GearEffectSnapshot snapshot,boolean direct,boolean melee,boolean hostile,
                          boolean noProc,boolean reflected,boolean blocked,double actualHpLoss,
                          double targetHealth,double targetNormalMaximum,Kind targetKind,
                          double noncriticalPhysical,Map<GearCombatEffects.Channel,Double> preMitigation,
                          double procCoefficient,boolean protectedTarget,boolean scriptedVeto,
                          boolean creditedKill) {
        public Contact {
            Objects.requireNonNull(world);Objects.requireNonNull(owner);Objects.requireNonNull(item);Objects.requireNonNull(target);
            Objects.requireNonNull(root);Objects.requireNonNull(contact);Objects.requireNonNull(snapshot);
            if(root.isBlank()||contact.isBlank())throw new IllegalArgumentException("INVALID_PROC_ID");
            Objects.requireNonNull(targetKind);preMitigation=Map.copyOf(preMitigation);
            if(preMitigation.values().stream().anyMatch(value->!Double.isFinite(value)||value<0))
                throw new IllegalArgumentException("INVALID_PROC_CHANNEL");
            if(!Double.isFinite(actualHpLoss)||actualHpLoss<0||!Double.isFinite(targetHealth)||targetHealth<0
                    ||!Double.isFinite(targetNormalMaximum)||targetNormalMaximum<0
                    ||!Double.isFinite(noncriticalPhysical)||noncriticalPhysical<0
                    ||!Double.isFinite(procCoefficient)||procCoefficient<0||procCoefficient>1)
                throw new IllegalArgumentException("INVALID_PROC_CONTACT");
        }
    }
    public interface Port {
        void enqueue(Child child); // submit in a later native Gather, never in Filter or this receipt callback
        boolean execute(Contact contact); // existing protected native execute/death owner; true only when accepted
        List<UUID> burstTargets(Contact contact,double radius,int maximum); // actual LOS/hostility query
    }
    public interface Roll { double sample(String stableEventPurpose); }
    private record Pair(UUID world,UUID source,UUID target) {}
    private record Fragment(double amount,int charges,double expiry) {}
    private final Roll roll;
    private final Map<String,Double> seen=new HashMap<>();
    private final Map<Pair,Double> crushingLock=new HashMap<>(),armorLock=new HashMap<>(),reflectLock=new HashMap<>();
    private final Map<UUID,Double> fortifyLock=new HashMap<>(),burstLock=new HashMap<>();
    private final Map<UUID,Double> fortifyUntil=new HashMap<>();
    private final Map<UUID,Map<UUID,Double>> armorUntil=new HashMap<>();
    private final Map<Pair,ArrayDeque<Fragment>> fragments=new HashMap<>();
    private final Map<UUID,UUID> actorWorld=new HashMap<>();
    private static final int MAX=8192;
    public GearSignatureProcRuntime(Roll roll){this.roll=Objects.requireNonNull(roll);}
    public GearSignatureProcRuntime(DoubleSupplier roll){this(key->roll.getAsDouble());}
    private boolean chance(double percent,double coefficient,String key){
        if(percent<=0||coefficient<=0)return false;
        double value=roll.sample(key);
        if(!Double.isFinite(value)||value<0||value>=1)throw new IllegalArgumentException("INVALID_PROC_ROLL");
        return value<Math.min(1,percent*coefficient/100);
    }
    /** Called after ordinary critical selection and before native mitigation; returns the Physical amount. */
    public synchronized double deadly(GearEffectSnapshot snapshot,UUID item,String strike,double physical,boolean critical,
                         boolean itemAttack,boolean noProc,double coefficient){
        if(!Double.isFinite(physical)||physical<0)throw new IllegalArgumentException("INVALID_PHYSICAL");
        if(physical==0||critical||!itemAttack||noProc||snapshot.forItem(item).empty())return physical;
        return chance(snapshot.forItem(item).value("WA-136"),coefficient,strike+"/WA-136")?physical*2:physical;
    }
    /** Remaining Defense rating fraction; consumed by the existing DefenseView before mitigation. */
    public synchronized double armorRatingFactor(UUID target,double now){
        var rows=armorUntil.get(target);if(rows==null)return 1;
        rows.values().removeIf(until->until<=now);
        if(rows.isEmpty()){armorUntil.remove(target);return 1;}
        return .85;
    }
    /** One Less factor on incoming direct hits only. */
    public synchronized double incomingHitFactor(UUID owner,double now,boolean direct,boolean status){
        return direct&&!status&&fortifyUntil.getOrDefault(owner,0d)>now?.95:1;
    }
    public synchronized void clearWorld(UUID world){
        for(var actor:actorWorld.entrySet().stream().filter(row->row.getValue().equals(world)).map(Map.Entry::getKey).toList())clearActor(actor);
        seen.keySet().removeIf(key->key.startsWith(world+"/"));
        crushingLock.keySet().removeIf(key->key.world().equals(world));
        armorLock.keySet().removeIf(key->key.world().equals(world));
        reflectLock.keySet().removeIf(key->key.world().equals(world));
        fragments.keySet().removeIf(key->key.world().equals(world));
    }
    public synchronized void clearActor(UUID actor){
        actorWorld.remove(actor);
        seen.keySet().removeIf(key->key.contains("/"+actor+"/")||key.endsWith("/"+actor));
        fortifyUntil.remove(actor);fortifyLock.remove(actor);burstLock.remove(actor);
        armorUntil.remove(actor);armorUntil.values().forEach(row->row.remove(actor));
        fragments.keySet().removeIf(key->key.source().equals(actor)||key.target().equals(actor));
        crushingLock.keySet().removeIf(key->key.source().equals(actor)||key.target().equals(actor));
        armorLock.keySet().removeIf(key->key.source().equals(actor)||key.target().equals(actor));
        reflectLock.keySet().removeIf(key->key.source().equals(actor)||key.target().equals(actor));
    }
    private boolean claim(String id,double now){
        seen.entrySet().removeIf(row->row.getValue()<=now);
        if(seen.containsKey(id))return false;
        if(seen.size()>=MAX)throw new IllegalStateException("PROC_LEDGER_CAPACITY");
        seen.put(id,now+60);return true;
    }
    private void retireExpired(double now){
        crushingLock.values().removeIf(until->until<=now);
        armorLock.values().removeIf(until->until<=now);
        reflectLock.values().removeIf(until->until<=now);
        fortifyLock.values().removeIf(until->until<=now);
        fortifyUntil.values().removeIf(until->until<=now);
        burstLock.values().removeIf(until->until<=now);
        armorUntil.values().forEach(rows->rows.values().removeIf(until->until<=now));
        armorUntil.values().removeIf(Map::isEmpty);
        fragments.values().forEach(rows->rows.removeIf(fragment->fragment.expiry()<=now));
        fragments.values().removeIf(ArrayDeque::isEmpty);
    }
    private static boolean available(Map<Pair,Double> lock,Pair key,double now){return lock.getOrDefault(key,0d)<=now;}
    private static boolean permitted(Contact c){return c.direct()&&c.hostile()&&!c.noProc()&&!c.reflected()
            &&!c.blocked()&&c.actualHpLoss()>0&&!c.protectedTarget();}
    /** Called once for an authoritative post-application contact. */
    public synchronized void applied(Contact c,double now,Port port){
        if(!Double.isFinite(now)||now<0)throw new IllegalArgumentException("INVALID_PROC_TIME");
        Objects.requireNonNull(port);
        retireExpired(now);
        actorWorld.put(c.owner(),c.world());actorWorld.put(c.target(),c.world());
        if(!permitted(c)||c.snapshot().forItem(c.item()).empty())return;
        var local=c.snapshot().forItem(c.item());var pair=new Pair(c.world(),c.owner(),c.target());
        if(fragments.get(pair)==null&&local.value("WA-135")<=0&&local.value("WA-137")<=0
                &&local.value("WA-138")<=0&&local.value("WA-139")<=0&&local.value("WA-140")<=0)return;
        String key=c.world()+"/"+c.owner()+"/"+c.root()+"/"+c.contact();
        if(!claim(key,now))return;
        var records=fragments.get(pair);
        if(records!=null){
            records.removeIf(record->record.expiry()<=now||record.charges()<=0);
            var first=records.peekFirst();
            if(first!=null){
                port.enqueue(new Child(ChildKind.BARBED,c.world(),c.owner(),c.target(),c.root(),c.contact(),
                        Map.of(GearCombatEffects.Channel.PHYSICAL,first.amount())));
                records.removeFirst();if(first.charges()>1)records.addFirst(new Fragment(first.amount(),first.charges()-1,first.expiry()));
            }
            if(records.isEmpty())fragments.remove(pair);
        }
        if(c.targetHealth()>0&&c.noncriticalPhysical()>0&&c.targetKind()!=Kind.PLAYER
                &&c.targetKind()!=Kind.BOSS&&available(crushingLock,pair,now)
                &&chance(local.value("WA-135"),c.procCoefficient(),key+"/WA-135")){
            double fraction=c.targetKind()==Kind.ELITE?.02:.04;
            double amount=Math.min(fraction*c.targetHealth(),2*c.noncriticalPhysical());
            if(amount>0){crushingLock.put(pair,now+1);port.enqueue(new Child(ChildKind.CRUSHING,c.world(),c.owner(),c.target(),c.root(),c.contact(),
                    Map.of(GearCombatEffects.Channel.PHYSICAL,amount)));}
        }
        if(c.targetHealth()>0&&local.value("WA-137")>0&&c.noncriticalPhysical()>0
                &&chance(local.value("WA-137"),c.procCoefficient(),key+"/WA-137")){
            var queue=fragments.computeIfAbsent(pair,ignored->new ArrayDeque<>());
            if(queue.size()<3)queue.addLast(new Fragment(.08*c.noncriticalPhysical(),3,now+6));
        }
        if(c.targetHealth()>0&&c.targetKind()!=Kind.BOSS&&c.targetKind()!=Kind.PLAYER
                &&available(armorLock,pair,now)&&chance(local.value("WA-138"),c.procCoefficient(),key+"/WA-138")){
            armorLock.put(pair,now+5);armorUntil.computeIfAbsent(c.target(),ignored->new HashMap<>()).put(c.owner(),now+3);
        }
        if(c.melee()&&fortifyLock.getOrDefault(c.owner(),0d)<=now&&fortifyUntil.getOrDefault(c.owner(),0d)<=now
                &&chance(local.value("WA-140"),c.procCoefficient(),key+"/WA-140")){
            fortifyLock.put(c.owner(),now+5);fortifyUntil.put(c.owner(),now+3);
        }
        if(local.value("WA-139")>0&&c.targetHealth()>0&&!c.scriptedVeto()
                &&c.targetKind()!=Kind.BOSS&&c.targetKind()!=Kind.PLAYER
                &&c.targetNormalMaximum()>0&&c.targetHealth()<=.05*c.targetNormalMaximum()){
            String executeKey=c.world()+"/cull/"+c.target();
            if(!seen.containsKey(executeKey)){
                if(seen.size()>=MAX)throw new IllegalStateException("PROC_LEDGER_CAPACITY");
                if(port.execute(c))seen.put(executeKey,Double.POSITIVE_INFINITY);
            }
        }
    }
    /** Call only from the deduplicated native credited-death receipt, after reward ownership settles. */
    public synchronized void creditedKill(Contact c,double now,Port port){
        retireExpired(now);
        if(!Double.isFinite(now)||now<0||!c.creditedKill()||c.targetHealth()!=0
                ||!permitted(c)||c.snapshot().forItem(c.item()).empty())return;
        if(c.snapshot().forItem(c.item()).value("WA-143")<=0)return;
        if(!claim(c.world()+"/kill/"+c.owner()+"/"+c.root()+"/"+c.target(),now))return;
        actorWorld.put(c.owner(),c.world());actorWorld.put(c.target(),c.world());
        var local=c.snapshot().forItem(c.item());
        if(burstLock.getOrDefault(c.owner(),0d)<=now
                &&chance(local.value("WA-143"),c.procCoefficient(),c.world()+"/"+c.owner()+"/"+c.root()+"/WA-143")){
            burstLock.put(c.owner(),now+1);
            var channels=new EnumMap<GearCombatEffects.Channel,Double>(GearCombatEffects.Channel.class);
            c.preMitigation().forEach((channel,amount)->{if(amount>0)channels.put(channel,.35*amount);});
            if(!channels.isEmpty())for(var target:port.burstTargets(c,2,64))if(!target.equals(c.target())&&!target.equals(c.owner()))
                port.enqueue(new Child(ChildKind.KILL_BURST,c.world(),c.owner(),target,c.root(),c.contact(),channels));
        }
    }
    /** Completed hostile melee receipt against a wearer; independent of outgoing item identity. */
    public synchronized void received(UUID world,UUID wearer,UUID attacker,String root,String contact,GearEffectSnapshot equipment,
                         boolean directMelee,boolean hostile,boolean reflected,boolean blocked,double actualHpLoss,
                         boolean protectedAttacker,double now,Port port){
        retireExpired(now);
        if(!directMelee||!hostile||reflected||blocked||actualHpLoss<=0||protectedAttacker)return;
        double amount=equipment.value("WA-142");if(amount<=0)return;
        actorWorld.put(wearer,world);actorWorld.put(attacker,world);
        if(!claim(world+"/reflect/"+wearer+"/"+root+"/"+contact,now))return;
        var pair=new Pair(world,wearer,attacker);if(!available(reflectLock,pair,now))return;
        reflectLock.put(pair,now+.5);
        port.enqueue(new Child(ChildKind.RETRIBUTION,world,wearer,attacker,root,contact,
                Map.of(GearCombatEffects.Channel.PHYSICAL,amount)));
    }
}
