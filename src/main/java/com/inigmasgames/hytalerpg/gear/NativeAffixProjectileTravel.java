package com.inigmasgames.hytalerpg.gear;

import com.hypixel.hytale.server.core.modules.physics.component.Velocity;
import com.hypixel.hytale.server.core.modules.projectile.config.StandardPhysicsProvider;
import com.hypixel.hytale.server.core.modules.projectile.config.ImpactConsumer;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3d;
import java.util.UUID;

/** Per-launch WA-015/016 parameters for the native projectile spawn and physics owner. */
public final class NativeAffixProjectileTravel {
    private NativeAffixProjectileTravel() {}

    public record Launch(float speedMultiplier, double maximumTravel, float safetyLifetimeSeconds,
                         UUID sourceItem, String equipmentRevision) {
        public Launch {
            if (!Float.isFinite(speedMultiplier) || speedMultiplier <= 0 || !Double.isFinite(maximumTravel)
                    || maximumTravel <= 0 || !Float.isFinite(safetyLifetimeSeconds) || safetyLifetimeSeconds <= 0)
                throw new IllegalArgumentException("Invalid projectile launch");
        }
    }

    /** Caller supplies the actual authored travel budget and speed from its managed projectile profile. */
    public static Launch launch(GearEffectSnapshot accepted, UUID sourceItem, double baseSpeed,
                                double baseTravel, float authoredLifetimeSeconds, boolean instantLine) {
        if (instantLine || accepted == null || sourceItem == null || accepted.forItem(sourceItem).empty()
                || !(baseSpeed > 0) || !(baseTravel > 0) || !Double.isFinite(baseSpeed)
                || !Double.isFinite(baseTravel) || !(authoredLifetimeSeconds > 0)) return null;
        var local=accepted.forItem(sourceItem);
        double speedFactor=1+local.percent(GearEffectSnapshot.Operator.PROJECTILE_SPEED);
        double rangeFactor=1+local.percent(GearEffectSnapshot.Operator.PROJECTILE_RANGE);
        if (!(speedFactor > 0) || !(rangeFactor > 0) || !Double.isFinite(speedFactor)
                || !Double.isFinite(rangeFactor)) throw new IllegalArgumentException("Invalid projectile affix");
        if (speedFactor == 1 && rangeFactor == 1) return null;
        double budget=baseTravel*rangeFactor;
        // The native despawn is a safety ceiling. Actual termination is by the swept path ledger.
        double seconds=Math.max(authoredLifetimeSeconds, budget/(baseSpeed*speedFactor)*2);
        return new Launch((float)speedFactor,budget,(float)Math.min(seconds,Float.MAX_VALUE),
                sourceItem,accepted.revision());
    }

    /** The launch event runs after native PhysicsConfig.apply and before its first physics tick. */
    public static void applyNativeSpeed(Launch launch,Velocity velocity,StandardPhysicsProvider provider) {
        double factor=launch.speedMultiplier();
        provider.getVelocity().mul(factor);
        provider.getForceProviderStandardState().nextTickVelocity.mul(factor);
        velocity.set(new Vector3d(velocity.getVelocity()).mul(factor));
    }
    /** Preserve native impact handling within range, suppress only contacts past the path budget. */
    public static ImpactConsumer guardImpact(Path path,ImpactConsumer nativeImpact) {
        if(nativeImpact==null)return null;
        return (ref,point,block,entity,detail,buffer)->{
            if(path.contactWithinBudget(point))nativeImpact.onImpact(ref,point,block,entity,detail,buffer);
            else buffer.tryRemoveEntity(ref,RemoveReason.REMOVE);
        };
    }

    /** Attached to the projectile at launch; mutable only on that projectile's world thread. */
    public static final class Path implements Component<EntityStore> {
        private double maximum;
        private final Vector3d previous;
        private double traveled;
        public Path(){maximum=0;previous=new Vector3d();}
        public Path(Launch launch, Vector3d spawn) {
            maximum=launch.maximumTravel();previous=new Vector3d(spawn);
        }
        private Path(Path source) {
            maximum=source.maximum;previous=new Vector3d(source.previous);traveled=source.traveled;
        }
        @Override public Path clone(){return new Path(this);}
        public double traveled(){return traveled;}
        public double remaining(){return Math.max(0,maximum-traveled);}
        public boolean contactWithinBudget(Vector3d contact) {
            return contact!=null && Double.isFinite(contact.x) && Double.isFinite(contact.y)
                    && Double.isFinite(contact.z) && traveled+previous.distance(contact)<=maximum+1e-7;
        }
        /** Installed StandardPhysicsTickSystem ends each collision/bounce tick at one contact segment. */
        public boolean afterNativeMove(Vector3d current) {
            double segment=previous.distance(current);
            if (!Double.isFinite(segment)) throw new IllegalArgumentException("Invalid projectile transform");
            traveled+=segment;previous.set(current);
            return traveled>=maximum-1e-7;
        }
        /** Exact straight segment endpoint at the remaining budget, before native replication. */
        public Vector3d terminalPoint(Vector3d current) {
            double segment=previous.distance(current);
            if(!Double.isFinite(segment))throw new IllegalArgumentException("Invalid projectile transform");
            if(segment<=remaining()+1e-7)return new Vector3d(current);
            if(segment==0)return new Vector3d(previous);
            return new Vector3d(current).sub(previous).mul(remaining()/segment).add(previous);
        }
        /** A Return starts a distinct authorized leg; a native redirect keeps its remaining budget. */
        public void restart(Vector3d position,double legMaximum) {
            if(position==null||!Double.isFinite(legMaximum)||legMaximum<=0)
                throw new IllegalArgumentException("Invalid projectile return leg");
            maximum=legMaximum;traveled=0;previous.set(position);
        }
        public void relocate(Vector3d position){previous.set(position);}
        /** Pre-physics bound for straight travel. Curved/gravity motion requires native owner swept clipping. */
        public void limitStraightStep(Velocity velocity, float seconds) {
            if (!(seconds>0) || !Float.isFinite(seconds)) return;
            double step=velocity.getSpeed()*seconds;
            if (step>remaining() && step>0) velocity.set(new Vector3d(velocity.getVelocity()).mul(remaining()/step));
        }
        /** Keep the provider's authoritative velocity and replicated component in step. */
        public void limitNativeStep(Velocity velocity,StandardPhysicsProvider provider,float seconds) {
            if (!(seconds>0) || !Float.isFinite(seconds))return;
            double step=provider.getVelocity().length()*seconds;
            if(step<=remaining()||step<=0)return;
            double factor=remaining()/step;
            provider.getVelocity().mul(factor);
            provider.getForceProviderStandardState().nextTickVelocity.mul(factor);
            velocity.set(new Vector3d(velocity.getVelocity()).mul(factor));
        }
    }

}
