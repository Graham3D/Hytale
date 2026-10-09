package com.inigmasgames.hytalerpg.combat.resource;

import com.inigmasgames.hytalerpg.gear.GearEffectSnapshot;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/** World-thread recovery accounting; writers report confirmed native readback. */
public final class GearRecoveryRuntime {
    public enum Pool { HIT_HEALTH, HIT_MANA, KILL_HEALTH }
    public interface Credit { double admit(Pool pool,double requested,double currentMaximum); }
    public record Receipt(UUID actor, String world, String root, String contact, UUID victim,
                          double healthBefore, double healthAfter, boolean hostile, boolean directAttack,
                          boolean cancelled, boolean noLeech, boolean summonChild) {
        public Receipt {
            if(actor==null||world==null||world.isBlank()||root==null||root.isBlank()
                    ||contact==null||contact.isBlank()||victim==null)
                throw new IllegalArgumentException("INVALID_GEAR_RECOVERY_RECEIPT");
        }
        public double actualHealthLost() {
            return cancelled || !hostile || !directAttack || !Double.isFinite(healthBefore)
                    || !Double.isFinite(healthAfter) || healthBefore<=0 ? 0
                    : Math.min(healthBefore,Math.max(0,healthBefore-healthAfter));
        }
    }
    public record Payout(Pool pool, double amount) { }
    private record Key(UUID actor, String world, Pool pool) { }
    private record RootKey(UUID actor,String world,String root) { }
    private record KillKey(UUID actor,String world,String rewardId) { }
    private record Charge(double at, double amount) { }
    private static final class Pending {
        final double expires, creationMaximum;
        double amount;
        Pending(double expires,double creationMaximum,double amount) {
            this.expires=expires;this.creationMaximum=creationMaximum;this.amount=amount;
        }
    }
    private static final class Root {
        final double expires;
        final Set<String> contacts=new HashSet<>();
        boolean flatClaimed;
        Root(double now){expires=now+30;}
    }
    private static final int MAX_ROOTS=65536,MAX_CONTACTS_PER_ROOT=4096,MAX_KILLS=65536;
    private final Map<Key,ArrayDeque<Pending>> pending=new HashMap<>();
    private final Map<Key,ArrayDeque<Charge>> paid=new HashMap<>();
    private final LinkedHashMap<RootKey,Root> roots=new LinkedHashMap<>();
    private final LinkedHashMap<KillKey,Double> kills=new LinkedHashMap<>();
    private final Predicate<String> enabled;
    private final int maxRoots,maxKills;
    private double nextSweep;
    /** Production receives only authoritative, admitted equipment snapshots. */
    public GearRecoveryRuntime(){this(id->true,MAX_ROOTS,MAX_KILLS);}
    GearRecoveryRuntime(Predicate<String> enabled){this(enabled,MAX_ROOTS,MAX_KILLS);}
    GearRecoveryRuntime(Predicate<String> enabled,int maxRoots,int maxKills){
        this.enabled=java.util.Objects.requireNonNull(enabled);
        if(maxRoots<1||maxRoots>MAX_ROOTS||maxKills<1||maxKills>MAX_KILLS)
            throw new IllegalArgumentException("INVALID_RECOVERY_CAPACITY");
        this.maxRoots=maxRoots;this.maxKills=maxKills;
    }

    /** A root remains claimed for 30 seconds, including after payout/cancel. */
    public synchronized void onAttack(Receipt hit, GearEffectSnapshot gear, double healthMaximum,
                                      double spendableManaMaximum, double now) {
        onAttack(hit,gear,healthMaximum,spendableManaMaximum,now,false);
    }
    /** Sentinel inherits only the bound item's Health on Hit and Life Leech. */
    public synchronized void onAttack(Receipt hit, GearEffectSnapshot gear, double healthMaximum,
                                      double spendableManaMaximum, double now,boolean sentinel) {
        requireClock(now);if(hit==null||gear==null)throw new IllegalArgumentException("SOURCE_GEAR_REQUIRED");
        sweep(now);
        double lost=hit.actualHealthLost();
        if(lost<=0||hit.noLeech()||hit.summonChild())return;
        if(value(gear,"WA-094")<=0&&percent(gear,"WA-096")<=0
                &&(sentinel||value(gear,"WA-095")<=0&&percent(gear,"WA-097")<=0))return;
        if(!Double.isFinite(healthMaximum)||healthMaximum<0||!Double.isFinite(spendableManaMaximum)||spendableManaMaximum<0)
            throw new IllegalArgumentException("INVALID_RECOVERY_MAXIMUM");
        RootKey rootKey=new RootKey(hit.actor(),hit.world(),hit.root());
        Root root=roots.get(rootKey);
        if(root!=null&&root.expires<=now){roots.remove(rootKey);root=null;}
        if(root==null){
            if(roots.size()>=maxRoots)return;
            root=new Root(now);roots.put(rootKey,root);
        }
        if(root.contacts.size()>=MAX_CONTACTS_PER_ROOT||!root.contacts.add(hit.contact()+"/"+hit.victim()))return;
        if(!root.flatClaimed){
            root.flatClaimed=true;
            enqueue(new Key(hit.actor(),hit.world(),Pool.HIT_HEALTH),value(gear,"WA-094"),healthMaximum,now);
            if(!sentinel)enqueue(new Key(hit.actor(),hit.world(),Pool.HIT_MANA),value(gear,"WA-095"),spendableManaMaximum,now);
        }
        enqueue(new Key(hit.actor(),hit.world(),Pool.HIT_HEALTH),lost*percent(gear,"WA-096"),healthMaximum,now);
        if(!sentinel)enqueue(new Key(hit.actor(),hit.world(),Pool.HIT_MANA),lost*percent(gear,"WA-097"),spendableManaMaximum,now);
    }
    /** The durable reward owner must also reject replay beyond this one-hour local claim. */
    public synchronized void onKill(UUID actor,String world,String rewardId,boolean eligible,
                                    GearEffectSnapshot gear,double normalHealthMaximum,double now) {
        requireClock(now);sweep(now);
        if(actor==null||world==null||world.isBlank()||rewardId==null||rewardId.isBlank()||gear==null)
            throw new IllegalArgumentException("INVALID_KILL_RECOVERY");
        if(!Double.isFinite(normalHealthMaximum)||normalHealthMaximum<0)
            throw new IllegalArgumentException("INVALID_KILL_RECOVERY_MAXIMUM");
        if(!eligible||!enabled.test("WA-098")||gear.percent("WA-098")<=0)return;
        KillKey key=new KillKey(actor,world,rewardId);
        if(kills.containsKey(key)||kills.size()>=maxKills)return;
        kills.put(key,now+3600);
        enqueue(new Key(actor,world,Pool.KILL_HEALTH),normalHealthMaximum*gear.percent("WA-098"),normalHealthMaximum,now);
    }
    private double value(GearEffectSnapshot gear,String id){return enabled.test(id)?gear.value(id):0;}
    private double percent(GearEffectSnapshot gear,String id){return enabled.test(id)?gear.percent(id):0;}
    private void enqueue(Key key,double amount,double maximum,double now) {
        if(!Double.isFinite(amount)||amount<0||!Double.isFinite(maximum)||maximum<0)
            throw new IllegalArgumentException("INVALID_GEAR_RECOVERY_AMOUNT");
        if(amount<=0||maximum<=0)return;
        var queue=pending.computeIfAbsent(key,ignored->new ArrayDeque<>());
        if(queue.size()>=1024)return;
        queue.addLast(new Pending(now+3,maximum,amount));
    }
    /** Charges only confirmed native gain. Hit Health and hit Mana each share one rolling window. */
    public synchronized Payout pay(UUID actor,String world,Pool pool,double currentSpendableMaximum,
                                   double now,Credit credit) {
        requireClock(now);sweep(now);
        if(actor==null||world==null||world.isBlank()||pool==null||credit==null
                ||!Double.isFinite(currentSpendableMaximum)||currentSpendableMaximum<0)
            throw new IllegalArgumentException("INVALID_GEAR_RECOVERY_PAYOUT");
        Key key=new Key(actor,world,pool);
        var queue=pending.get(key);if(queue==null)return new Payout(pool,0);
        var window=paid.computeIfAbsent(key,ignored->new ArrayDeque<>());
        while(!window.isEmpty()&&window.peekFirst().at<=now-1)window.removeFirst();
        double paidInWindow=window.stream().mapToDouble(Charge::amount).sum();
        double total=0;
        double fraction=pool==Pool.HIT_MANA?.02:.04;
        while(!queue.isEmpty()) {
            Pending next=queue.peekFirst();
            if(next.expires<=now){queue.removeFirst();continue;}
            double limit=Math.max(0,Math.min(next.creationMaximum,currentSpendableMaximum)*fraction-paidInWindow);
            if(limit<=0)break;
            double request=Math.min(next.amount,limit);
            double actual;
            try { actual=credit.admit(pool,request,currentSpendableMaximum); }
            catch(RuntimeException failed) { queue.removeFirst();break; }
            if(!Double.isFinite(actual)||actual<0||actual>request+1e-8){queue.removeFirst();break;}
            if(actual<=0)break;
            window.addLast(new Charge(now,actual));paidInWindow+=actual;total+=actual;
            next.amount-=actual;
            if(next.amount<=1e-8)queue.removeFirst();
            if(actual+1e-8<request)break;
        }
        if(queue.isEmpty())pending.remove(key);
        return new Payout(pool,total);
    }
    /** Cancels unpaid credit; windows and replay claims survive logout/death. */
    public synchronized void cancel(UUID actor) {pending.keySet().removeIf(key->key.actor.equals(actor));}
    public synchronized void cancelWorld(String world) {pending.keySet().removeIf(key->key.world.equals(world));}
    public synchronized boolean hasPending(UUID actor,String world,Pool pool){
        return pending.containsKey(new Key(actor,world,pool));
    }
    public synchronized void maintain(double now){requireClock(now);sweep(now);}
    private void sweep(double now){
        if(now<nextSweep)return;
        nextSweep=now+1;
        roots.values().removeIf(root->root.expires<=now);
        kills.values().removeIf(expiry->expiry<=now);
        Iterator<Map.Entry<Key,ArrayDeque<Charge>>> windows=paid.entrySet().iterator();
        while(windows.hasNext()){
            var window=windows.next().getValue();
            while(!window.isEmpty()&&window.peekFirst().at<=now-1)window.removeFirst();
            if(window.isEmpty())windows.remove();
        }
        Iterator<Map.Entry<Key,ArrayDeque<Pending>>> queues=pending.entrySet().iterator();
        while(queues.hasNext()){
            var queue=queues.next().getValue();
            while(!queue.isEmpty()&&queue.peekFirst().expires<=now)queue.removeFirst();
            if(queue.isEmpty())queues.remove();
        }
    }
    private static void requireClock(double now) {
        if(!Double.isFinite(now)||now<0)throw new IllegalArgumentException("INVALID_GEAR_RECOVERY_CLOCK");
    }
}
