package com.inigmasgames.hytalerpg.execution.hytale;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.corecomponents.combat.ActionAttack;
import com.hypixel.hytale.server.npc.corecomponents.combat.builders.BuilderActionAttack;
import com.hypixel.hytale.server.npc.instructions.ExecutionSupport;
import com.hypixel.hytale.server.npc.movement.Steering;
import com.hypixel.hytale.server.npc.movement.controllers.MotionControllerWalk;
import com.hypixel.hytale.server.npc.movement.controllers.builders.BuilderMotionControllerWalk;
import com.hypixel.hytale.server.npc.role.Role;
import com.hypixel.hytale.server.npc.sensorinfo.InfoProvider;
import java.util.Objects;
import org.joml.Vector3d;

/** Thin native property adapters. No animation, action, status, or displacement implementation. */
public final class NativeNpcTiming {
    public static final String WALK_TYPE="HywindEnemyWalk", ATTACK_TYPE="HywindEnemyAttack";
    private NativeNpcTiming(){}

    public interface Provider {
        /** Called at the safe native navigation update, after native movement admission. */
        default double movement(Ref<EntityStore> actor,Role role,ComponentAccessor<EntityStore> store){return 1;}
        /** Null means this exact native action has no certified scalable recovery. */
        default Recovery recovery(Ref<EntityStore> actor,Role role,String actualRoot,Store<EntityStore> store){return null;}
    }
    private static volatile Provider provider=new Provider(){};
    public static void configure(Provider value){provider=Objects.requireNonNull(value);}

    /** Frozen at action acceptance, never read again during the swing. Protected duration includes the complete native chain. */
    public record Recovery(String rootId,String bindingRevision,double protectedSeconds,double rate) {
        public Recovery {
            if(rootId==null||rootId.isBlank()||bindingRevision==null||bindingRevision.isBlank()
                    ||!Double.isFinite(protectedSeconds)||protectedSeconds<=0||protectedSeconds>60)
                throw new IllegalArgumentException("NPC_RECOVERY_PROFILE_INVALID");
            rate=validRate(rate);
        }
        public double pause(String actualRoot,double nativePause){
            if(!rootId.equals(actualRoot))throw new IllegalStateException("NPC_RECOVERY_ROOT_MISMATCH");
            if(!Double.isFinite(nativePause)||nativePause<0)throw new IllegalArgumentException("NPC_NATIVE_PAUSE_INVALID");
            // Identity must be bit-for-bit native; fixed/short pauses and the native chain remain unchanged.
            if(rate==1||nativePause<=protectedSeconds)return nativePause;
            return protectedSeconds+(nativePause-protectedSeconds)/rate;
        }
    }
    /** Exact installed bite graph and 30-frame animation both protect the first 0.5 seconds.
     * Unknown IDs cannot silently acquire a guessed recovery duration. */
    public static double protectedSeconds(String profileId){
        return switch(profileId){
            case "Larva_Void_Bite/installed-0.7.0-pre.5.1"->.5;
            default->throw new IllegalArgumentException("NPC_RECOVERY_PROFILE_UNCERTIFIED:"+profileId);
        };
    }
    private static double validRate(double value){
        if(!Double.isFinite(value)||value<1||value>1.5)throw new IllegalArgumentException("NPC_ME_TIMING_RATE_INVALID");
        return value;
    }

    public static class Walk extends MotionControllerWalk {
        private double movement=1;
        public Walk(BuilderMotionControllerWalk builder,BuilderSupport support){super(builder,support);}
        @Override protected double computeMove(Ref<EntityStore> actor,Role role,Steering steering,double dt,
                Vector3d translation,ComponentAccessor<EntityStore> store){
            movement=validRate(provider.movement(actor,role,store));
            return super.computeMove(actor,role,steering,dt,translation,store);
        }
        @Override public double getMaximumSpeed(){return super.getMaximumSpeed()*movement;}
    }
    public static final class WalkBuilder extends BuilderMotionControllerWalk {
        @Override public MotionControllerWalk build(BuilderSupport support){return new Walk(this,support);}
    }

    public static class Attack extends ActionAttack {
        private Ref<EntityStore> acceptingActor;
        private Store<EntityStore> acceptingStore;
        public Attack(BuilderActionAttack builder,BuilderSupport support){super(builder,support);}
        @Override public boolean execute(Ref<EntityStore> actor,ExecutionSupport execution,InfoProvider info,double dt,Store<EntityStore> store){
            if(acceptingStore!=null)throw new IllegalStateException("NPC_ATTACK_REENTRANT");
            acceptingActor=actor;acceptingStore=store;
            try{return super.execute(actor,execution,info,dt,store);}
            finally{acceptingActor=null;acceptingStore=null;}
        }
        @Override protected double newAttackPause(){
            double nativePause=super.newAttackPause(); // Exactly one native RNG sample, even for an identity projection.
            if(acceptingStore==null)return nativePause;
            var frozen=provider.recovery(acceptingActor,ownerRole,attackInteraction,acceptingStore);
            return frozen==null?nativePause:frozen.pause(attackInteraction,nativePause);
        }
    }
    public static final class AttackBuilder extends BuilderActionAttack {
        @Override public ActionAttack build(BuilderSupport support){return new Attack(this,support);}
    }
}
