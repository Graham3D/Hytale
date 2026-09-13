package com.inigmasgames.hytalerpg.execution.hytale;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.*;
import com.hypixel.hytale.protocol.packets.entities.EntityUpdates;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.*;
import java.util.function.*;

/** Client-only native application-effect loop. Never inserted into a saved EffectController.
 * Eight distinct cosmetic effect IDs allow independent simultaneous roots on the same caster. */
final class HealingChannelAudio {
    static final int MAX_ROOTS=512,SLOTS=8;
    static final String SOUND="SFX_Deployable_Totem_Heal_Effect_Local";
    private record Session(UUID owner,int slot,int networkId,int effect,Consumer<EntityUpdates> send){}
    private final Map<String,Session> sessions=new HashMap<>();
    void start(Store<EntityStore> store,UUID owner,String root){
        if(sessions.containsKey(root))return;
        var ref=store.getExternalData().getRefFromUUID(owner);if(ref==null||!ref.isValid())return;
        var player=store.getComponent(ref,PlayerRef.getComponentType());var network=store.getComponent(ref,NetworkId.getComponentType());
        if(player==null||network==null)return;
        start(root,owner,network.getId(),slot->EntityEffect.getAssetMap().getIndex("RPG_Healing_Audio_"+slot),p->player.getPacketHandler().write(p));
    }
    private void start(String root,UUID owner,int networkId,IntUnaryOperator resolve,Consumer<EntityUpdates> send){
        if(sessions.containsKey(root))return;
        if(sessions.size()>=MAX_ROOTS)throw new IllegalStateException("HEAL_AUDIO_CAPACITY");
        int used=0;for(var s:sessions.values())if(s.owner.equals(owner))used|=1<<s.slot;
        int slot=0;while(slot<SLOTS&&(used&(1<<slot))!=0)slot++;
        if(slot==SLOTS)throw new IllegalStateException("HEAL_AUDIO_OWNER_CAPACITY");
        int effect=resolve.applyAsInt(slot);if(effect<0)throw new IllegalStateException("HEAL_AUDIO_ASSET_MISSING");
        var session=new Session(owner,slot,networkId,effect,send);sessions.put(root,session);
        try{send.accept(packet(session,false));}catch(RuntimeException failure){sessions.remove(root);try{send.accept(packet(session,true));}catch(RuntimeException cleanup){failure.addSuppressed(cleanup);}throw failure;}
    }
    void stop(String root){var session=sessions.remove(root);if(session!=null)session.send.accept(packet(session,true));}
    private static EntityUpdates packet(Session s,boolean remove){
        var effects=new EntityEffectsUpdate();
        effects.entityEffectUpdates=new EntityEffectUpdate[]{new EntityEffectUpdate(remove?EffectOp.Remove:EffectOp.Add,s.effect,0,!remove,false,null)};
        return new EntityUpdates(null,new EntityUpdate[]{new EntityUpdate(s.networkId,null,new ComponentUpdate[]{effects})});
    }
    static void audit(){
        if(!Boolean.getBoolean("rpg.projectileSpawnAudit"))throw new IllegalStateException("ISOLATED_AUDIT_DISABLED");
        var audio=new HealingChannelAudio();var packets=new ArrayList<EntityUpdates>();var owner=UUID.randomUUID();
        IntUnaryOperator assets=i->EntityEffect.getAssetMap().getIndex("RPG_Healing_Audio_"+i);
        for(int i=0;i<100;i++)audio.start("root",owner,42,assets,packets::add);
        if(packets.size()!=1||audio.sessions.size()!=1)throw new IllegalStateException("HEAL_AUDIO_RESTARTED");
        audio.start("second",owner,42,assets,packets::add);
        if(audio.sessions.get("root").effect==audio.sessions.get("second").effect)throw new IllegalStateException("HEAL_AUDIO_ROOT_COLLISION");
        for(int i=2;i<SLOTS;i++)audio.start("extra"+i,owner,42,assets,packets::add);
        try{audio.start("over",owner,42,assets,packets::add);throw new AssertionError("Audio cap bypassed");}catch(IllegalStateException expected){if(!expected.getMessage().equals("HEAL_AUDIO_OWNER_CAPACITY"))throw expected;}
        for(var key:List.copyOf(audio.sessions.keySet()))audio.stop(key);
        audio.stop("root");
        if(packets.size()!=16||!audio.sessions.isEmpty())throw new IllegalStateException("HEAL_AUDIO_CLEANUP");
        for(int i=0;i<16;i++){
            var updates=(EntityEffectsUpdate)packets.get(i).updates[0].updates[0];var effect=updates.entityEffectUpdates[0];
            if(effect.type!=(i<8?EffectOp.Add:EffectOp.Remove)||effect.infinite!=(i<8))throw new IllegalStateException("HEAL_AUDIO_PACKET");
        }
        com.hypixel.hytale.logger.HytaleLogger.getLogger().atInfo().log("RPG_HEAL_AUDIO_NATIVE revision=R032-AP result=PASS loopAsset=true startOnce=true rootIsolation=true stopSameInstance=true serverPersistence=false connectedProof=false");
    }
}
