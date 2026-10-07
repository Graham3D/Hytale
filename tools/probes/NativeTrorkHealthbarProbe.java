package com.inigmasgames.hytalerpg.probes;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entityui.UIComponentList;
import com.hypixel.hytale.server.core.modules.entityui.asset.EntityUIComponent;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.inigmasgames.hytalerpg.enemies.EnemyRewardContext;
import com.inigmasgames.hytalerpg.execution.hytale.HytaleDifficultyCombat;
import java.util.Arrays;
import java.util.UUID;

/** Offline-compiled, unregistered QA prototype. R209 does not load or call this class. */
public final class NativeTrorkHealthbarProbe {
    public record Snapshot(UUID world, UUID entity, String[] previous, String[] shown) { }

    /** Invoke only on the world writer thread after QA birth publication, never inside an ECS tick callback. */
    public static Snapshot attach(Store<EntityStore> store, Ref<EntityStore> actor, HytaleDifficultyCombat difficulty) {
        if (!store.isInThread() || actor == null || !actor.isValid()) throw new IllegalStateException("PROBE_WRITER_REQUIRED");
        UUID world=store.getExternalData().getWorld().getWorldConfig().getUuid();
        var identity=store.getComponent(actor, UUIDComponent.getComponentType());
        var npc=store.getComponent(actor, NPCEntity.getComponentType());
        if(identity==null || npc==null || !"Trork_Warrior".equals(npc.getRoleName()))
            throw new IllegalArgumentException("PROBE_TRORK_REQUIRED");
        UUID entity=identity.getUuid();
        var state=difficulty.enemyState(world,entity).orElseThrow(()->new IllegalArgumentException("PROBE_MANAGED_ACTOR_REQUIRED"));
        if(state.descriptor().spawnOrigin()!=EnemyRewardContext.Origin.QA
                || !"Trork_Warrior".equals(state.descriptor().nativeRoleId()))
            throw new IllegalArgumentException("PROBE_QA_TRORK_REQUIRED");
        var stats=store.getComponent(actor,EntityStatMap.getComponentType());
        if(stats==null || stats.get(DefaultEntityStatTypes.getHealth())==null)
            throw new IllegalStateException("PROBE_REAL_HEALTH_REQUIRED");
        var component=EntityUIComponent.getAssetMap().getAsset("Healthbar");
        if(component==null || component.toPacket().type!=com.hypixel.hytale.protocol.EntityUIType.EntityStat
                || component.toPacket().entityStatIndex!=DefaultEntityStatTypes.getHealth())
            throw new IllegalStateException("PROBE_NATIVE_HEALTHBAR_ASSET_REQUIRED");
        var current=store.getComponent(actor,UIComponentList.getComponentType());
        if(current==null)throw new IllegalStateException("PROBE_R209_HIDDEN_BASELINE_REQUIRED");
        String[] previous=names(current);
        if(Arrays.asList(previous).contains("Healthbar"))throw new IllegalStateException("PROBE_ALREADY_VISIBLE");
        String[] shown=Arrays.copyOf(previous,previous.length+1);
        shown[previous.length]="Healthbar";
        store.putComponent(actor,UIComponentList.getComponentType(),new UIComponentList(shown));
        return new Snapshot(world,entity,previous,shown);
    }

    /** Restore only if no other owner changed the component list in the meantime. */
    public static boolean restore(Store<EntityStore> store, Ref<EntityStore> actor, Snapshot snapshot) {
        if(!store.isInThread()||actor==null||!actor.isValid())return false;
        var identity=store.getComponent(actor,UUIDComponent.getComponentType());
        var current=store.getComponent(actor,UIComponentList.getComponentType());
        if(identity==null||current==null||!snapshot.entity().equals(identity.getUuid())
                ||!snapshot.world().equals(store.getExternalData().getWorld().getWorldConfig().getUuid())
                ||!Arrays.equals(snapshot.shown(),names(current)))return false;
        store.putComponent(actor,UIComponentList.getComponentType(),new UIComponentList(snapshot.previous()));
        return true;
    }

    private static String[] names(UIComponentList list) {
        var assets=EntityUIComponent.getAssetMap();
        return Arrays.stream(list.getComponentIds()).mapToObj(id->{
            var asset=assets.getAsset(id);
            if(asset==null)throw new IllegalStateException("PROBE_UNKNOWN_UI_COMPONENT:"+id);
            return asset.getId();
        }).toArray(String[]::new);
    }

    private NativeTrorkHealthbarProbe() { }
}
