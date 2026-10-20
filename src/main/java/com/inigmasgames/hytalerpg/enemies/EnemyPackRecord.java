package com.inigmasgames.hytalerpg.enemies;

import com.inigmasgames.hytalerpg.execution.math.Vec3;
import java.util.*;

/** Durable sealed roster. ECS absence, unload, conversion and administrative removal are not defeats. */
public record EnemyPackRecord(int schemaVersion,UUID packId,UUID worldId,UUID encounterId,long generation,
                             State state,State suspendedFrom,Vec3 anchor,List<Member> birthRoster,
                             UUID leaderId,Set<UUID> guardIds,Map<UUID,String> deadMemberReceipts,
                             boolean sealedRoster,boolean packboundReleased,String spawnPlanReceipt,String abortReason) {
    public enum State { RESERVED, STAGED, GUARDED, RELEASED, SUSPENDED, DEFEATED, ABORTED }
    public enum Role { MEMBER, LEADER, MINION }
    public record Member(UUID logicalActorId,UUID nativeEntityId,String canonicalRoleId,Role role) {
        public Member {Objects.requireNonNull(logicalActorId);Objects.requireNonNull(nativeEntityId);Objects.requireNonNull(role);
            EnemyAffixRegistry.require(canonicalRoleId!=null&&!canonicalRoleId.isBlank(),"PACK_MEMBER_ROLE");}
    }
    public EnemyPackRecord {
        Objects.requireNonNull(packId);Objects.requireNonNull(worldId);Objects.requireNonNull(encounterId);Objects.requireNonNull(state);Objects.requireNonNull(anchor);
        birthRoster=List.copyOf(birthRoster);
        // The encounter store validates the exact JSON shape after decoding; persisted sets need stable order.
        guardIds=Collections.unmodifiableSortedSet(new TreeSet<>(guardIds));
        deadMemberReceipts=Collections.unmodifiableSortedMap(new TreeMap<>(deadMemberReceipts));
        EnemyAffixRegistry.require(schemaVersion==1&&generation>=0&&!birthRoster.isEmpty()&&birthRoster.size()<=8,"PACK_SCHEMA_OR_SIZE");
        EnemyAffixRegistry.require(spawnPlanReceipt!=null&&!spawnPlanReceipt.isBlank()&&spawnPlanReceipt.length()<=512,"PACK_SPAWN_PLAN_RECEIPT");
        var members=new HashMap<UUID,Member>();var nativeIds=new HashSet<UUID>();
        for(var member:birthRoster){EnemyAffixRegistry.require(members.putIfAbsent(member.logicalActorId(),member)==null&&nativeIds.add(member.nativeEntityId()),"PACK_DUPLICATE_MEMBER");}
        long leaders=birthRoster.stream().filter(m->m.role()==Role.LEADER).count();
        EnemyAffixRegistry.require(leaderId==null?birthRoster.size()>=(spawnPlanReceipt.startsWith("qa-command/")?1:2)
                &&leaders==0&&birthRoster.stream().allMatch(m->m.role()==Role.MEMBER):
                leaders==1&&members.containsKey(leaderId)&&members.get(leaderId).role()==Role.LEADER
                        &&birthRoster.stream().allMatch(m->m.role()!=Role.MEMBER),"PACK_LEADER_RELATION");
        EnemyAffixRegistry.require(guardIds.isEmpty()||leaderId!=null&&guardIds.size()>=2&&guardIds.size()<=5&&guardIds.stream().allMatch(id->members.containsKey(id)&&members.get(id).role()==Role.MINION),"PACK_GUARD_ROSTER");
        EnemyAffixRegistry.require(members.keySet().containsAll(deadMemberReceipts.keySet()),"FOREIGN_PACK_DEFEAT");
        for(var receipt:deadMemberReceipts.entrySet())EnemyAffixRegistry.require(receipt.getValue().equals(defeatId(worldId,members.get(receipt.getKey()).nativeEntityId())),"PACK_DEFEAT_ID");
        EnemyAffixRegistry.require((state==State.SUSPENDED)==(suspendedFrom!=null),"PACK_SUSPENDED_STATE");
        if(suspendedFrom!=null)EnemyAffixRegistry.require(suspendedFrom==State.GUARDED||suspendedFrom==State.RELEASED,"PACK_SUSPENDED_ORIGIN");
        EnemyAffixRegistry.require((state==State.ABORTED)==(abortReason!=null&&!abortReason.isBlank()),"PACK_ABORT_REASON");
        EnemyAffixRegistry.require(state==State.RESERVED||sealedRoster,"UNSEALED_PUBLISHED_PACK");
        if(!guardIds.isEmpty()) {
            boolean allDead=deadMemberReceipts.keySet().containsAll(guardIds);
            EnemyAffixRegistry.require(packboundReleased==allDead,"PACK_RELEASE_RECEIPTS_MISMATCH");
            if(state==State.GUARDED||suspendedFrom==State.GUARDED)EnemyAffixRegistry.require(!allDead,"GUARDED_WITHOUT_GUARDS");
            if(state==State.RELEASED||state==State.DEFEATED||suspendedFrom==State.RELEASED)EnemyAffixRegistry.require(allDead,"RELEASE_BEFORE_GUARD_DEFEATS");
        } else EnemyAffixRegistry.require(!packboundReleased&&state!=State.GUARDED&&suspendedFrom!=State.GUARDED,"PACKBOUND_WITHOUT_GUARDS");
        if(state==State.DEFEATED)EnemyAffixRegistry.require(deadMemberReceipts.size()==birthRoster.size(),"PACK_DEFEATED_WITH_LIVING_MEMBERS");
    }
    public int livingGuards(){return (int)guardIds.stream().filter(id->!deadMemberReceipts.containsKey(id)).count();}
    public boolean blocksExternalMutation(UUID logicalActor){
        if(!contains(logicalActor))return false;
        if(deadMemberReceipts.containsKey(logicalActor))return true;
        return state==State.RESERVED||state==State.STAGED||state==State.SUSPENDED||state==State.ABORTED||state==State.DEFEATED
                ||Objects.equals(leaderId,logicalActor)&&!guardIds.isEmpty()&&!packboundReleased;
    }
    public boolean blocksConversion(UUID logicalActor){
        return contains(logicalActor)&&(blocksExternalMutation(logicalActor)||!packboundReleased&&(guardIds.contains(logicalActor)||Objects.equals(leaderId,logicalActor))&&!guardIds.isEmpty());
    }
    public boolean economicAdmission(UUID logicalActor){return contains(logicalActor)&&(state==State.GUARDED||state==State.RELEASED);}
    public boolean contains(UUID actor){return birthRoster.stream().anyMatch(m->m.logicalActorId().equals(actor));}
    public EnemyPackRecord staged(){requireState(State.RESERVED);return copy(State.STAGED,null,deadMemberReceipts,true,packboundReleased,null);}
    public EnemyPackRecord publish(){requireState(State.STAGED);return copy(guardIds.isEmpty()?State.RELEASED:State.GUARDED,null,deadMemberReceipts,true,false,null);}
    public EnemyPackRecord suspend(){if(state==State.SUSPENDED)return this;
        EnemyAffixRegistry.require(state==State.GUARDED||state==State.RELEASED,"PACK_CANNOT_SUSPEND");return copy(State.SUSPENDED,state,deadMemberReceipts,true,packboundReleased,null);}
    public EnemyPackRecord resume(){requireState(State.SUSPENDED);return copy(suspendedFrom,null,deadMemberReceipts,true,packboundReleased,null);}
    public EnemyPackRecord abort(String reason){if(state==State.ABORTED)return this;
        EnemyAffixRegistry.require(state!=State.DEFEATED,"DEFEATED_PACK_CANNOT_ABORT");return copy(State.ABORTED,null,deadMemberReceipts,true,packboundReleased,Objects.requireNonNull(reason));}
    /** Called by the encounter store only after its existing immutable terminal receipt is durable. */
    public EnemyPackRecord terminalDefeat(UUID logicalActor,String durableReceipt) {
        var existing=deadMemberReceipts.get(logicalActor);
        if(existing!=null){EnemyAffixRegistry.require(existing.equals(durableReceipt),"CONFLICTING_PACK_DEFEAT");return this;}
        EnemyAffixRegistry.require(state==State.GUARDED||state==State.RELEASED||state==State.SUSPENDED,"PACK_NOT_PUBLISHED");
        EnemyAffixRegistry.require(contains(logicalActor),"FOREIGN_PACK_DEFEAT");
        // A guarded leader cannot acquire a terminal combat receipt. Suspend doesn't revoke earlier durable receipts.
        EnemyAffixRegistry.require(!Objects.equals(leaderId,logicalActor)||guardIds.isEmpty()||packboundReleased,"GUARDED_LEADER_DEFEAT");
        var receipts=new HashMap<>(deadMemberReceipts);receipts.put(logicalActor,durableReceipt);
        boolean released=!guardIds.isEmpty()&&receipts.keySet().containsAll(guardIds);
        State next=state,prior=suspendedFrom;
        if(released&&state==State.GUARDED)next=State.RELEASED;
        if(released&&state==State.SUSPENDED)prior=State.RELEASED;
        if(receipts.size()==birthRoster.size()){next=State.DEFEATED;prior=null;}
        return copy(next,prior,receipts,true,released,null);
    }
    private void requireState(State expected){EnemyAffixRegistry.require(state==expected,"PACK_TRANSITION:"+state+" expected "+expected);}
    private EnemyPackRecord copy(State next,State prior,Map<UUID,String> receipts,boolean sealed,boolean released,String reason){
        return new EnemyPackRecord(schemaVersion,packId,worldId,encounterId,generation,next,prior,anchor,birthRoster,leaderId,guardIds,receipts,sealed,released,spawnPlanReceipt,reason);
    }
    private static String defeatId(UUID world,UUID entity){return "enemy-death/"+world+"/"+entity;}
}
