package com.inigmasgames.hytalerpg.execution.hytale;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAttachment;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.inigmasgames.hytalerpg.enemies.*;
import java.util.*;

/** Native texture substitution at descriptor publication/rebind; persistent native role/model identity stays intact. */
public final class HytaleEnemyPalette {
    private HytaleEnemyPalette(){}
    public record Projection(UUID world,UUID entity,long generation,Model previous,Model shown){}
    public static Model textureOnly(Model nativeModel,String texture){
        Objects.requireNonNull(nativeModel);EnemyVisualVariants.textureReference(texture);
        return copy(nativeModel,texture,nativeModel.getAttachments());
    }
    private static Model copy(Model nativeModel,String texture,ModelAttachment[] attachments){
        return new Model(nativeModel.getModelAssetId(),nativeModel.getScale(),nativeModel.getRandomAttachmentIds(),attachments,
                nativeModel.getBoundingBox(),nativeModel.getModel(),texture,nativeModel.getGradientSet(),nativeModel.getGradientId(),
                nativeModel.getEyeHeight(),nativeModel.getCrouchOffset(),nativeModel.getSittingOffset(),nativeModel.getSleepingOffset(),
                nativeModel.getAnimationSetMap(),nativeModel.getCamera(),nativeModel.getLight(),nativeModel.getParticles(),nativeModel.getTrails(),
                nativeModel.getPhysicsValues(),nativeModel.getDetailBoxes(),nativeModel.getPhobia(),nativeModel.getPhobiaModelAssetId());
    }
    /** Resolve all replacements against the original snapshot, never cascade one override into another. */
    public static Model texturesOnly(Model nativeModel,List<EnemyVisualVariants.TextureOverride> overrides){
        Objects.requireNonNull(nativeModel);var replacements=new HashMap<String,String>();
        for(var override:overrides){
            if(!override.modelAssetId().equals(nativeModel.getModelAssetId()))throw new IllegalStateException("ENEMY_PALETTE_MODEL_MISMATCH");
            if(replacements.putIfAbsent(override.originalTextureAssetId(),override.replacementTextureAssetId())!=null)
                throw new IllegalArgumentException("DUPLICATE_TEXTURE_OVERRIDE");
        }
        var seen=new HashSet<String>();String texture=nativeModel.getTexture();
        if(replacements.containsKey(texture)){seen.add(texture);texture=replacements.get(texture);}
        var original=nativeModel.getAttachments();var attachments=original==null?null:original.clone();
        if(attachments!=null)for(int i=0;i<attachments.length;i++){
            var attachment=attachments[i];var replacement=replacements.get(attachment.getTexture());
            if(replacement==null)continue;seen.add(attachment.getTexture());
            attachments[i]=new ModelAttachment(attachment.getModel(),replacement,attachment.getGradientSet(),attachment.getGradientId(),attachment.getWeight());
        }
        if(!seen.equals(replacements.keySet()))throw new IllegalStateException("ENEMY_PALETTE_TEXTURE_OWNERSHIP_CONFLICT");
        return copy(nativeModel,texture,attachments);
    }
    public static Optional<Projection> apply(Store<EntityStore> store,Ref<EntityStore> ref,EnemyDescriptor descriptor,EnemyVisualVariants registry){
        if(!store.isInThread())throw new IllegalStateException("ENEMY_PALETTE_WRONG_WORLD_THREAD");
        if(ref==null||!ref.isValid()||ref.getStore()!=store||!store.getExternalData().getWorld().getWorldConfig().getUuid().equals(descriptor.worldId()))
            throw new IllegalArgumentException("ENEMY_PALETTE_WORLD_BINDING");
        var id=store.getComponent(ref,UUIDComponent.getComponentType());var npc=store.getComponent(ref,NPCEntity.getComponentType());
        if(id==null||!id.getUuid().equals(descriptor.entityId())||npc==null||!npc.getRoleName().equals(descriptor.nativeRoleId()))
            throw new IllegalArgumentException("ENEMY_PALETTE_ACTOR_BINDING");
        if(descriptor.paletteId()==null)return Optional.empty();
        var variant=registry.find(descriptor.paletteId()).orElseThrow(()->new IllegalStateException("ENEMY_FROZEN_PALETTE_MISSING"));
        var component=store.getComponent(ref,ModelComponent.getComponentType());var model=component==null?null:component.getModel();
        if(model==null||!variant.canonicalRoleId().equals(descriptor.canonicalRoleId())
                ||variant.superUniqueTemplateId()!=null&&(descriptor.templateBirth()==null||!variant.superUniqueTemplateId().equals(descriptor.templateBirth().templateId())))
            throw new IllegalStateException("ENEMY_PALETTE_MODEL_MISMATCH");
        var shown=texturesOnly(model,variant.textureOverrides());
        store.replaceComponent(ref,ModelComponent.getComponentType(),new ModelComponent(shown));
        return Optional.of(new Projection(descriptor.worldId(),descriptor.entityId(),descriptor.encounterGeneration(),model,shown));
    }
    public static void restore(Store<EntityStore> store,Ref<EntityStore> ref,Projection projection){
        if(!store.isInThread())throw new IllegalStateException("ENEMY_PALETTE_WRONG_WORLD_THREAD");
        if(ref==null||!ref.isValid()||ref.getStore()!=store||!projection.world().equals(store.getExternalData().getWorld().getWorldConfig().getUuid()))return;
        var id=store.getComponent(ref,UUIDComponent.getComponentType());var model=store.getComponent(ref,ModelComponent.getComponentType());
        if(id!=null&&projection.entity().equals(id.getUuid())&&model!=null&&model.getModel()==projection.shown())
            store.replaceComponent(ref,ModelComponent.getComponentType(),new ModelComponent(projection.previous()));
    }
}
