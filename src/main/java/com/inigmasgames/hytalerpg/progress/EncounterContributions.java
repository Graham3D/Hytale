package com.inigmasgames.hytalerpg.progress;

import com.inigmasgames.hytalerpg.execution.math.Vec3;
import java.util.*;

/** Bounded server-only eligibility ledger. No client payload, requested damage or trace log is evidence. */
public final class EncounterContributions {
    public static final int MAX_ENCOUNTERS=4096,MAX_CONTRIBUTORS=256,MAX_SUPPORT_ENCOUNTERS=64;
    public static final int MAX_TOTAL_CONTRIBUTIONS=16384;
    public static final long CONTRIBUTION_WINDOW_MS=20_000,FARM_WINDOW_MS=60_000;
    public enum Kind { DAMAGE, HEAL, ABSORB, CONTROL, TAUNT }
    private record Key(UUID world,UUID enemy){}
    private record ActorKey(UUID world,UUID player){}
    public record Participant(UUID player,UUID world,Vec3 position,int level,boolean loaded,String partyId,LearningSources.Opportunity learning){
        public Participant(UUID player,UUID world,Vec3 position,int level,boolean loaded,String partyId){this(player,world,position,level,loaded,partyId,null);}
        public Participant {Objects.requireNonNull(player);Objects.requireNonNull(world);Objects.requireNonNull(position);
            if(level<1||level>99||partyId!=null&&(partyId.isBlank()||partyId.length()>128))throw new IllegalArgumentException("INVALID_PARTICIPANT");}
    }
    public record Credit(UUID player,Kind kind,long observedAtMillis,double actualAmount){
        public Credit {Objects.requireNonNull(player);Objects.requireNonNull(kind);if(observedAtMillis<0||!finitePositive(actualAmount))throw new IllegalArgumentException("INVALID_CONTRIBUTION");}
    }
    public record Share(UUID player,long xp,long insight,int commonPotPlayerLevel,int eligiblePartyMembers,LearningSources.Opportunity learning){
        public Share(UUID player,long xp,long insight,int commonPotPlayerLevel,int eligiblePartyMembers){this(player,xp,insight,commonPotPlayerLevel,eligiblePartyMembers,null);}
        public Share {Objects.requireNonNull(player);if(xp<1||insight<1||commonPotPlayerLevel<1||commonPotPlayerLevel>99||eligiblePartyMembers<1||eligiblePartyMembers>MAX_CONTRIBUTORS)throw new IllegalArgumentException("INVALID_DEATH_SHARE");}
    }
    /** Save original context and anti-farm watermark with credits; reload must not start a fresh encounter. */
    public record Snapshot(EnemyRewardRegistry.Spawn spawn,List<Credit> credits,long firstCombat,long progressAt,
                           long lastObserved,double lowestHealthFraction,boolean disqualified){
        public Snapshot {
            Objects.requireNonNull(spawn);credits=List.copyOf(credits);
            if(credits.size()>MAX_CONTRIBUTORS||lastObserved<spawn.spawnedAtMillis()||!Double.isFinite(lowestHealthFraction)
                    ||lowestHealthFraction<0||lowestHealthFraction>1||firstCombat< -1||progressAt< -1
                    ||(firstCombat==-1)!=(progressAt==-1)||firstCombat>lastObserved||progressAt>lastObserved
                    ||(firstCombat>=0&&(firstCombat<spawn.spawnedAtMillis()||progressAt<firstCombat))
                    ||(firstCombat<0&&(!credits.isEmpty()||lowestHealthFraction!=1)))throw new IllegalArgumentException("INVALID_ENCOUNTER_SNAPSHOT");
            Set<UUID> unique=new HashSet<>();
            for(var c:credits)if(!unique.add(c.player())||c.player().equals(spawn.enemy())||c.observedAtMillis()<firstCombat||c.observedAtMillis()>lastObserved)throw new IllegalArgumentException("INVALID_SNAPSHOT_CREDIT");
        }
        public Snapshot invalidated(){return new Snapshot(spawn,credits,firstCombat,progressAt,lastObserved,lowestHealthFraction,true);}
    }
    public record DeathPlan(EnemyRewardRegistry.Spawn spawn,Vec3 deathPosition,long deathAtMillis,List<Share> shares){
        public DeathPlan {Objects.requireNonNull(spawn);Objects.requireNonNull(deathPosition);shares=List.copyOf(shares);
            if(deathAtMillis<spawn.spawnedAtMillis()||shares.size()>MAX_CONTRIBUTORS)throw new IllegalArgumentException("INVALID_DEATH_PLAN");
            Set<UUID> unique=new HashSet<>();
            for(var share:shares)if(!unique.add(share.player())||share.insight()!=spawn.rank().insight
                    ||share.xp()!=ProgressionMath.equalShare(ProgressionMath.enemyReward(spawn.level(),spawn.rank(),spawn.rarity(),share.commonPotPlayerLevel()),share.eligiblePartyMembers()))throw new IllegalArgumentException("DEATH_SHARE_PROFILE_MISMATCH");}
        public EarnedReward reward(Share share){
            if(!shares.contains(share))throw new IllegalArgumentException("NOT_AN_ELIGIBLE_DEATH_SHARE");
            return new EarnedReward(spawn.eventId(),share.xp(),share.insight(),Map.of(),"ELIGIBLE_ENEMY_DEATH","","",spawn.enemy().toString());
        }
    }
    private static final class Encounter {
        final EnemyRewardRegistry.Spawn spawn;final Map<UUID,Credit> contributors=new HashMap<>();
        long firstCombat=-1,progressAt=-1,lastObserved;double lowestHealthFraction=1;boolean disqualified;DeathPlan death;
        Encounter(EnemyRewardRegistry.Spawn spawn){this.spawn=spawn;lastObserved=spawn.spawnedAtMillis();}
    }
    private final Map<Key,Encounter> encounters=new LinkedHashMap<>();
    private final Map<ActorKey,Set<Key>> actorEncounters=new HashMap<>();
    private int contributionCount;
    public synchronized boolean begin(EnemyRewardRegistry.Spawn spawn){
        var key=new Key(spawn.world(),spawn.enemy());var current=encounters.get(key);
        if(current!=null){if(!current.spawn.equals(spawn))throw new IllegalStateException("SPAWN_CONTEXT_CHANGED");return !current.disqualified;}
        if(encounters.size()>=MAX_ENCOUNTERS)return false;encounters.put(key,new Encounter(spawn));return true;
    }
    public synchronized Snapshot snapshot(UUID world,UUID enemy){
        var e=encounters.get(new Key(world,enemy));if(e==null)throw new IllegalStateException("UNREGISTERED_ENCOUNTER");
        var credits=e.contributors.values().stream().sorted(Comparator.comparing(c->c.player().toString())).toList();
        return new Snapshot(e.spawn,credits,e.firstCombat,e.progressAt,e.lastObserved,e.lowestHealthFraction,e.disqualified);
    }
    /** Admission is checked in full before changing any index. Never overwrite a live encounter. */
    public synchronized boolean restore(Snapshot saved){
        var key=new Key(saved.spawn().world(),saved.spawn().enemy());
        if(encounters.containsKey(key))throw new IllegalStateException("ENCOUNTER_ALREADY_LOADED");
        if(encounters.size()>=MAX_ENCOUNTERS||contributionCount+saved.credits().size()>MAX_TOTAL_CONTRIBUTIONS)return false;
        for(var credit:saved.credits())if(actorEncounters.getOrDefault(new ActorKey(key.world(),credit.player()),Set.of()).size()>=MAX_SUPPORT_ENCOUNTERS)return false;
        var e=new Encounter(saved.spawn());e.firstCombat=saved.firstCombat();e.progressAt=saved.progressAt();e.lastObserved=saved.lastObserved();
        e.lowestHealthFraction=saved.lowestHealthFraction();e.disqualified=saved.disqualified();
        for(var credit:saved.credits()){
            e.contributors.put(credit.player(),credit);actorEncounters.computeIfAbsent(new ActorKey(key.world(),credit.player()),ignored->new LinkedHashSet<>()).add(key);
        }
        contributionCount+=e.contributors.size();encounters.put(key,e);return true;
    }
    /** A conversion/ownership transition invalidates the encounter for its entire lifetime, even after restoration. */
    public synchronized void disqualify(UUID world,UUID enemy){var e=encounters.get(new Key(world,enemy));if(e!=null)e.disqualified=true;}
    public synchronized boolean damage(UUID world,UUID enemy,UUID player,double before,double after,double maximum,boolean hostile,long now){
        if(!hostile||!finitePositive(maximum)||!Double.isFinite(before)||!Double.isFinite(after)||before<=after||after<0||before>maximum)return false;
        var e=active(world,enemy,now);if(e==null)return false;
        if(!credit(e,player,Kind.DAMAGE,before-after,now))return false;
        // Once 60 seconds of unchanged interaction expires, the master requires a NEW enemy,
        // not one extra point of damage to rehabilitate the same captive encounter.
        double fraction=after/maximum;if(now-e.progressAt<=FARM_WINDOW_MS&&fraction<e.lowestHealthFraction-1e-9){e.lowestHealthFraction=fraction;e.progressAt=now;}
        return true;
    }
    /** Caller supplies actual consumed hostile absorption, not shield capacity or a cast request. */
    public synchronized boolean absorb(UUID world,UUID enemy,UUID provider,double consumed,boolean hostile,long now){
        var e=hostile?active(world,enemy,now):null;return e!=null&&credit(e,provider,Kind.ABSORB,consumed,now);
    }
    public synchronized boolean control(UUID world,UUID enemy,UUID actor,boolean actuallyChanged,boolean taunt,boolean hostile,long now){
        var e=actuallyChanged&&hostile?active(world,enemy,now):null;return e!=null&&credit(e,actor,taunt?Kind.TAUNT:Kind.CONTROL,1,now);
    }
    /** No last-hit requirement. Native adapter must exclude overheal/self-cost/friendly-sparring restoration. */
    public synchronized int heal(UUID world,UUID healer,UUID beneficiary,double actualEligibleHealing,boolean allyAllowed,long now){
        return healSelected(world,healer,beneficiary,actualEligibleHealing,allyAllowed,now,null);
    }
    /** Deferred native observations cannot acquire a context that joined AFTER their event-time frontier. */
    public synchronized int healSelected(UUID world,UUID healer,UUID beneficiary,double actualEligibleHealing,boolean allyAllowed,long now,Set<UUID> selected){
        if(!allyAllowed||!finitePositive(actualEligibleHealing))return 0;int credited=0,examined=0;
        for(var key:List.copyOf(actorEncounters.getOrDefault(new ActorKey(world,beneficiary),Set.of()))){
            if(selected!=null&&!selected.contains(key.enemy()))continue;
            var e=encounters.get(key);if(e==null||e.death!=null||e.disqualified)continue;
            var recipient=e.contributors.get(beneficiary);if(recipient==null||!recent(recipient.observedAtMillis(),now))continue;
            if(++examined>MAX_SUPPORT_ENCOUNTERS)break;
            if(active(world,e.spawn.enemy(),now)!=null&&credit(e,healer,Kind.HEAL,actualEligibleHealing,now))credited++;
        }return credited;
    }
    public synchronized List<UUID> healingEncounters(UUID world,UUID beneficiary,long now){
        List<UUID> result=new ArrayList<>();
        for(var key:actorEncounters.getOrDefault(new ActorKey(world,beneficiary),Set.of())){
            var e=encounters.get(key);if(e==null||e.disqualified||e.death!=null)continue;
            var credit=e.contributors.get(beneficiary);if(credit!=null&&recent(credit.observedAtMillis(),now))result.add(key.enemy());
        }return List.copyOf(result);
    }
    public synchronized boolean masteryEligible(UUID world,UUID enemy,UUID player,int playerLevel,long now){
        if(playerLevel<1||playerLevel>99)return false;var e=active(world,enemy,now);
        if(e==null||e.spawn.level()-playerLevel<=-11||e.firstCombat<0||now-e.progressAt>FARM_WINDOW_MS)return false;
        var contribution=e.contributors.get(player);return contribution!=null&&recent(contribution.observedAtMillis(),now);
    }
    /** Freeze once. Repeated native death callbacks reuse the same positions, pot and eligible set. */
    public synchronized DeathPlan death(UUID world,UUID enemy,Vec3 position,long now,List<Participant> participants){
        var e=encounters.get(new Key(world,enemy));if(e==null)throw new IllegalStateException("UNREGISTERED_ENCOUNTER");
        if(e.death!=null)return e.death;
        if(participants.size()>MAX_CONTRIBUTORS)throw new IllegalArgumentException("PARTICIPANT_QUERY_BUDGET");
        if(now<e.lastObserved||now<e.spawn.spawnedAtMillis())throw new IllegalStateException("ENCOUNTER_CLOCK_REVERSED");
        Map<String,List<Participant>> groups=new TreeMap<>();Set<UUID> seen=new HashSet<>();
        for(var participant:participants){
            if(!seen.add(participant.player()))throw new IllegalArgumentException("DUPLICATE_PARTICIPANT");
            var credit=e.contributors.get(participant.player());
            if(e.disqualified||!participant.loaded()||!participant.world().equals(world)||credit==null||!recent(credit.observedAtMillis(),now)
                    ||distanceSquared(participant.position(),position)>64*64)continue;
            String group=participant.partyId()==null?"solo/"+participant.player():"party/"+participant.partyId();
            groups.computeIfAbsent(group,ignored->new ArrayList<>()).add(participant);
        }
        List<Share> shares=new ArrayList<>();
        for(var group:groups.values()){
            // Explicit conservative engineering policy for the unspecified mixed-level common party pot.
            int commonLevel=group.stream().mapToInt(Participant::level).max().orElseThrow();
            long pot=ProgressionMath.enemyReward(e.spawn.level(),e.spawn.rank(),e.spawn.rarity(),commonLevel);
            long xp=ProgressionMath.equalShare(pot,group.size());
            group.stream().sorted(Comparator.comparing(p->p.player().toString())).forEach(p->shares.add(new Share(p.player(),xp,e.spawn.rank().insight,commonLevel,group.size(),p.learning())));
        }
        e.death=new DeathPlan(e.spawn,position,now,shares);return e.death;
    }
    public synchronized void remove(UUID world,UUID enemy){
        var key=new Key(world,enemy);var removed=encounters.remove(key);if(removed==null)return;
        contributionCount-=removed.contributors.size();
        for(var player:removed.contributors.keySet()){
            var actor=new ActorKey(world,player);var entries=actorEncounters.get(actor);entries.remove(key);if(entries.isEmpty())actorEncounters.remove(actor);
        }
    }
    public synchronized void unload(UUID world){for(var key:List.copyOf(encounters.keySet()))if(key.world().equals(world))remove(world,key.enemy());}
    public synchronized int size(){return encounters.size();}
    private Encounter active(UUID world,UUID enemy,long now){
        var e=encounters.get(new Key(world,enemy));
        if(e==null||e.disqualified||e.death!=null||now<e.spawn.spawnedAtMillis()||now<e.lastObserved)return null;
        e.lastObserved=now;return e;
    }
    private boolean credit(Encounter e,UUID player,Kind kind,double actual,long now){
        if(player==null||player.equals(e.spawn.enemy())||!finitePositive(actual)||(!e.contributors.containsKey(player)&&e.contributors.size()>=MAX_CONTRIBUTORS))return false;
        if(!e.contributors.containsKey(player)){
            var actor=new ActorKey(e.spawn.world(),player);var indexed=actorEncounters.getOrDefault(actor,Set.of());
            if(indexed.size()>=MAX_SUPPORT_ENCOUNTERS||contributionCount>=MAX_TOTAL_CONTRIBUTIONS)return false;
            actorEncounters.computeIfAbsent(actor,ignored->new LinkedHashSet<>()).add(new Key(e.spawn.world(),e.spawn.enemy()));contributionCount++;
        }
        if(e.firstCombat<0){e.firstCombat=now;e.progressAt=now;}
        e.contributors.put(player,new Credit(player,kind,now,actual));return true;
    }
    private static boolean recent(long at,long now){return now>=at&&now-at<=CONTRIBUTION_WINDOW_MS;}
    private static boolean finitePositive(double value){return Double.isFinite(value)&&value>0;}
    private static double distanceSquared(Vec3 a,Vec3 b){double x=a.x()-b.x(),y=a.y()-b.y(),z=a.z()-b.z();return x*x+y*y+z*z;}
}
