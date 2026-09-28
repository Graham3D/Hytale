package com.inigmasgames.hytalerpg.gear;

import java.util.*;

/** Frozen party facts from a trusted server provider, never client-submitted party IDs. */
public final class GearClaims {
    private GearClaims(){}
    public enum Mode { SOLO, ROUND_ROBIN, PARTY_FFA, LEADER }
    public record Policy(String group,long revision,Mode mode,List<UUID> joinOrder,UUID leader,Set<UUID> consent,boolean outOfCombat,boolean bossOnly) {
        public Policy {
            Objects.requireNonNull(group);Objects.requireNonNull(mode);joinOrder=List.copyOf(joinOrder);consent=Set.copyOf(consent);
            if(group.isBlank()||group.length()>160||revision<0||joinOrder.isEmpty()||joinOrder.size()>256||new HashSet<>(joinOrder).size()!=joinOrder.size()
                    ||!joinOrder.contains(leader)||!new HashSet<>(joinOrder).containsAll(consent)||mode==Mode.SOLO&&joinOrder.size()!=1)throw new IllegalArgumentException("Invalid frozen party policy");
            if((mode==Mode.PARTY_FFA||mode==Mode.LEADER)&&(!outOfCombat||!consent.containsAll(joinOrder)))throw new IllegalArgumentException("Unanimous out-of-combat consent required");
        }
        public static Policy solo(UUID actor){return new Policy("solo/"+actor,0,Mode.SOLO,List.of(actor),actor,Set.of(),true,false);}
    }
    public record Claim(Policy policy,long firstOrder,long lastContribution){public Claim{Objects.requireNonNull(policy);if(firstOrder<0||lastContribution<0)throw new IllegalArgumentException("Claim time");}}
    public record Ledger(List<Claim> claims,long nextOrder){
        public static final Ledger EMPTY=new Ledger(List.of(),0);
        public Ledger{claims=List.copyOf(claims);if(claims.size()>256||nextOrder<0)throw new IllegalArgumentException("Claim capacity");}
        public Ledger contribute(UUID actor,Policy current,long observed){
            if(!current.joinOrder().contains(actor))throw new IllegalArgumentException("Contributor absent from policy");
            var rows=new ArrayList<>(claims);
            // Freeze identity/membership once. A kick or policy change cannot replace pending rights.
            for(int i=0;i<rows.size();i++)if(rows.get(i).policy().joinOrder().contains(actor)){
                var old=rows.get(i);rows.set(i,new Claim(old.policy(),old.firstOrder(),Math.max(old.lastContribution(),observed)));return new Ledger(rows,nextOrder);
            }
            if(rows.stream().anyMatch(c->c.policy().group().equals(current.group())))return this; // Late join cannot replace frozen membership.
            rows.add(new Claim(current,nextOrder,observed));return new Ledger(rows,Math.addExact(nextOrder,1));
        }
        public Optional<Claim> select(Set<UUID> eligible,long deathTime){
            var candidates=claims.stream().filter(c->c.policy().joinOrder().stream().anyMatch(eligible::contains)).sorted(Comparator.comparingLong(Claim::firstOrder)).toList();
            var active=candidates.stream().filter(c->deathTime>=c.lastContribution()&&deathTime-c.lastContribution()<=20_000).findFirst();
            return active.isPresent()?active:candidates.stream().findFirst();
        }
    }
    public record Allocation(Mode mode,String group,long policyRevision,List<UUID> eligible,UUID sponsor,UUID assigned,UUID frozenLeader,long exclusiveUntil,long expiresAt,long revision,UUID claimedBy,List<String> audit){
        public Allocation{Objects.requireNonNull(mode);Objects.requireNonNull(group);eligible=List.copyOf(eligible);audit=List.copyOf(audit);
            if(eligible.isEmpty()||eligible.size()>256||new HashSet<>(eligible).size()!=eligible.size()||!eligible.contains(sponsor)
                    ||assigned!=null&&!eligible.contains(assigned)||claimedBy!=null&&!eligible.contains(claimedBy)||expiresAt<0||exclusiveUntil>expiresAt||revision<0||audit.size()>256)throw new IllegalArgumentException("Invalid loot allocation");}
        public String denial(UUID actor,long now){
            if(claimedBy!=null)return "Already picked up";if(now>=expiresAt)return "Loot expired";
            if(!eligible.contains(actor))return "Not in the frozen eligible group";
            if(mode==Mode.PARTY_FFA||mode==Mode.ROUND_ROBIN&&now>=exclusiveUntil)return "";
            return actor.equals(assigned)?"":"Assigned to "+assigned;
        }
        public String display(long now){return now>=expiresAt?"Loot expired":(mode==Mode.PARTY_FFA||mode==Mode.ROUND_ROBIN&&now>=exclusiveUntil?"Party shared":"Assigned to "+assigned+(mode==Mode.ROUND_ROBIN?" ("+Math.max(0,(exclusiveUntil-now+999)/1000)+"s protected)":""))+"; expires in "+Math.max(0,(expiresAt-now+999)/1000)+"s";}
        public Allocation pickup(UUID actor,long expected,long now){if(expected!=revision)throw new IllegalArgumentException("Stale ownership revision");String denial=denial(actor,now);if(!denial.isEmpty())throw new IllegalArgumentException(denial);
            return new Allocation(mode,group,policyRevision,eligible,sponsor,assigned,frozenLeader,exclusiveUntil,expiresAt,revision+1,actor,audit);}
        public Allocation assign(UUID leader,UUID recipient,long expected,long now){
            if(mode!=Mode.LEADER||!leader.equals(frozenLeader)||!eligible.contains(recipient)||claimedBy!=null||now>=expiresAt||expected!=revision)throw new IllegalArgumentException("Allocation denied");
            var events=new ArrayList<>(audit);events.add(leader+" assigned to "+recipient+" at "+now);
            return new Allocation(mode,group,policyRevision,eligible,sponsor,recipient,frozenLeader,exclusiveUntil,expiresAt,revision+1,null,events);
        }
    }
    public static Allocation allocate(Claim claim,Set<UUID> eligible,long cursor,boolean boss,long committedAt,GearRandom random){
        var policy=claim.policy();var pool=policy.joinOrder().stream().filter(eligible::contains).toList();if(pool.isEmpty())throw new IllegalArgumentException("No eligible claimant");
        Mode mode=policy.mode();if(mode==Mode.LEADER&&(!pool.contains(policy.leader())||policy.bossOnly()&&!boss))mode=Mode.ROUND_ROBIN;
        UUID sponsor;
        if(mode==Mode.LEADER)sponsor=policy.leader();else if(mode==Mode.PARTY_FFA)sponsor=pool.get(random.stream("sponsor").nextInt(pool.size()));
        else if(mode==Mode.SOLO)sponsor=pool.getFirst();else{
            sponsor=null;for(int offset=0;offset<policy.joinOrder().size();offset++){var id=policy.joinOrder().get((int)Math.floorMod(cursor+offset,policy.joinOrder().size()));if(pool.contains(id)){sponsor=id;break;}}
            Objects.requireNonNull(sponsor);
        }
        return new Allocation(mode,policy.group(),policy.revision(),pool,sponsor,mode==Mode.PARTY_FFA?null:sponsor,policy.leader(),mode==Mode.ROUND_ROBIN?Math.addExact(committedAt,30_000):committedAt,Math.addExact(committedAt,180_000),0,null,List.of());
    }
}
