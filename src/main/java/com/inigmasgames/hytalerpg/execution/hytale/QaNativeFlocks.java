package com.inigmasgames.hytalerpg.execution.hytale;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.NonSerialized;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.entity.group.EntityGroup;
import com.hypixel.hytale.server.flock.FlockMembership;
import com.hypixel.hytale.server.flock.FlockMembershipSystems;
import com.hypixel.hytale.server.flock.FlockPlugin;
import com.hypixel.hytale.server.flock.config.FlockAsset;
import com.inigmasgames.hytalerpg.enemies.EnemyBirthPlan;
import com.inigmasgames.hytalerpg.enemies.EnemyDescriptor;

/** Connects an already selected QA pack to Hytale's own flock membership lifecycle. */
final class QaNativeFlocks {
    private QaNativeFlocks() {}

    static void attach(Store<EntityStore> store, EnemyBirthPlan birth) {
        if (birth.actors().size() <= 1) return;
        if (!store.isInThread() || birth.actors().getFirst().packRole() != EnemyDescriptor.PackRole.LEADER
                || birth.actors().stream().skip(1).anyMatch(a -> a.packRole() != EnemyDescriptor.PackRole.MINION))
            throw new IllegalStateException("QA_FLOCK_ROSTER_INVALID");
        var asset = FlockAsset.getAssetMap().getAsset(birth.actors().size()>3?"Group_Large":"Group_Small");
        if (asset == null) throw new IllegalStateException("QA_NATIVE_FLOCK_ASSET_MISSING");
        String role = birth.actors().getFirst().nativeRoleId();
        var flock = FlockPlugin.createFlock(store, asset, new String[]{role});
        if (flock == null || !flock.isValid()) throw new IllegalStateException("QA_NATIVE_FLOCK_UNAVAILABLE");
        try {
            store.addComponent(flock, EntityStore.REGISTRY.getNonSerializedComponentType(), NonSerialized.get());
            for (var actor : birth.actors()) {
                if (!actor.nativeRoleId().equals(role)) throw new IllegalStateException("QA_NATIVE_FLOCK_MIXED_ROLE");
                Ref<EntityStore> ref = store.getExternalData().getRefFromUUID(actor.entityId());
                if (ref == null || !ref.isValid() || store.getComponent(ref, FlockMembership.getComponentType()) != null
                        || !FlockMembershipSystems.canJoinFlock(ref, flock, store))
                    throw new IllegalStateException("QA_NATIVE_FLOCK_MEMBER_UNAVAILABLE:" + actor.entityId());
                FlockMembershipSystems.join(ref, flock, store);
            }
        } catch (RuntimeException failure) {
            if (flock.isValid()) store.removeEntity(flock, RemoveReason.REMOVE);
            throw failure;
        }
        com.hypixel.hytale.logger.HytaleLogger.getLogger().atInfo().log(
                "RPG_ENEMY_QA_NATIVE_FLOCK encounter=%s leader=%s minions=%s",
                birth.encounter(),birth.actors().getFirst().entityId(),birth.actors().size()-1);
    }

    static void inspect(Store<EntityStore> store, EnemyBirthPlan birth) {
        if (birth.actors().size() <= 1) return;
        var leader=store.getExternalData().getRefFromUUID(birth.actors().getFirst().entityId());
        var membership=leader==null||!leader.isValid()?null:store.getComponent(leader,FlockMembership.getComponentType());
        var flock=membership==null?null:membership.getFlockRef();
        var group=flock==null||!flock.isValid()?null:store.getComponent(flock,EntityGroup.getComponentType());
        int attached=0;
        for(var actor:birth.actors()){
            var ref=store.getExternalData().getRefFromUUID(actor.entityId());
            var member=ref==null||!ref.isValid()?null:store.getComponent(ref,FlockMembership.getComponentType());
            if(member!=null&&flock!=null&&flock.equals(member.getFlockRef()))attached++;
        }
        com.hypixel.hytale.logger.HytaleLogger.getLogger().atInfo().log(
                "RPG_ENEMY_QA_NATIVE_FLOCK_FINAL encounter=%s leaderCorrect=%s attached=%s expected=%s nativeGroupSize=%s membershipType=%s",
                birth.encounter(),group!=null&&leader.equals(group.getLeaderRef()),attached,birth.actors().size(),
                group==null?"missing":group.size(),membership==null?"missing":membership.getMembershipType());
    }
}
