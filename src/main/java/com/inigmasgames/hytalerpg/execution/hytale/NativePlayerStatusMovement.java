package com.inigmasgames.hytalerpg.execution.hytale;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.MovementSettings;
import com.hypixel.hytale.server.core.entity.entities.player.movement.MovementManager;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** One reversible, exact strongest Slow contribution to the native replicated player speed. */
public final class NativePlayerStatusMovement {
    private record Contribution(java.lang.ref.WeakReference<MovementSettings> settings,float baseline,float applied) { }
    private final Map<UUID,Contribution> contributions=new HashMap<>();

    public void synchronize(UUID id,Ref<EntityStore> player,Store<EntityStore> store,double slow){
        if(id==null||player==null||store==null||!Double.isFinite(slow)||slow<0||slow>1)
            throw new IllegalArgumentException("INVALID_PLAYER_STATUS_MOVEMENT");
        if(!player.isValid()){forget(id);return;}
        var manager=store.getComponent(player,MovementManager.getComponentType());
        var playerRef=store.getComponent(player,PlayerRef.getComponentType());
        if(manager==null||playerRef==null){forget(id);return;}
        MovementSettings settings=manager.getSettings();
        if(settings==null)throw new IllegalStateException("NATIVE_PLAYER_MOVEMENT_SETTINGS_MISSING");
        contributions.values().removeIf(c->c.settings().get()==null);
        Contribution previous=contributions.get(id);
        float current=settings.baseSpeed;
        float baseline=previous!=null&&previous.settings().get()==settings
                &&Float.compare(current,previous.applied())==0?previous.baseline():current;
        float desired=contributedSpeed(baseline,slow);
        if(Float.compare(current,desired)!=0){settings.baseSpeed=desired;manager.update(playerRef.getPacketHandler());}
        if(slow==0)contributions.remove(id);
        else {
            if(contributions.size()>=4096&&!contributions.containsKey(id))
                throw new IllegalStateException("PLAYER_STATUS_MOVEMENT_BUDGET");
            contributions.put(id,new Contribution(new java.lang.ref.WeakReference<>(settings),baseline,desired));
        }
    }
    public void forget(UUID id){contributions.remove(id);}
    public static float contributedSpeed(float baseline,double slow){
        if(!Float.isFinite(baseline)||baseline<0||!Double.isFinite(slow)||slow<0||slow>1)
            throw new IllegalArgumentException("INVALID_PLAYER_STATUS_MOVEMENT");
        return (float)(baseline*(1-slow));
    }
}
