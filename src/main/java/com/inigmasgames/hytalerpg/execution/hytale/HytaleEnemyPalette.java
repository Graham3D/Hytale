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
    private static final String TRORK_MODEL="Trork_Warrior";
    private static final String TRORK_BASE="NPC/Intelligent/Trork/Models/";
    private static final String TRORK_VISUAL="NPC/RPG/Enemies/Visual/Trork/";
    private static final Set<String> CAPABILITY_LOGGED=java.util.concurrent.ConcurrentHashMap.newKeySet();
    public record Projection(UUID world,UUID entity,long generation,Model previous,Model shown){}
    public static Model textureOnly(Model nativeModel,String texture){
        Objects.requireNonNull(nativeModel);EnemyVisualVariants.textureReference(texture);
        return copy(nativeModel,texture,nativeModel.getAttachments(),nativeModel.getScale());
    }
    private static Model copy(Model nativeModel,String texture,ModelAttachment[] attachments,float scale){
        return new Model(nativeModel.getModelAssetId(),scale,nativeModel.getRandomAttachmentIds(),attachments,
                nativeModel.getBoundingBox(),nativeModel.getModel(),texture,nativeModel.getGradientSet(),nativeModel.getGradientId(),
                nativeModel.getEyeHeight(),nativeModel.getCrouchOffset(),nativeModel.getSittingOffset(),nativeModel.getSleepingOffset(),
                nativeModel.getAnimationSetMap(),nativeModel.getCamera(),nativeModel.getLight(),nativeModel.getParticles(),nativeModel.getTrails(),
                nativeModel.getPhysicsValues(),nativeModel.getDetailBoxes(),nativeModel.getPhobia(),nativeModel.getPhobiaModelAssetId());
    }
    /** Root model packet scale only; preserves the native collision box, physics, attachments and texture. */
    public static Model visualScaleOnly(Model nativeModel,float nativeBaseline,EnemyRarity rarity){
        Objects.requireNonNull(nativeModel);Objects.requireNonNull(rarity);
        if(!Float.isFinite(nativeBaseline)||nativeBaseline<=0)throw new IllegalArgumentException("ENEMY_NATIVE_VISUAL_SCALE");
        float target=nativeBaseline*rarityScale(rarity);
        if(!Float.isFinite(target)||target<=0)throw new IllegalArgumentException("ENEMY_ELITE_VISUAL_SCALE");
        if(Float.compare(nativeModel.getScale(),target)==0)return nativeModel;
        return copy(nativeModel,nativeModel.getTexture(),nativeModel.getAttachments(),target);
    }
    public static float rarityScale(EnemyRarity rarity){
        return switch(Objects.requireNonNull(rarity)){
            case NORMAL, BOSS -> 1.0f;case CHAMPION -> 1.15f;case UNIQUE -> 1.30f;case SUPER_UNIQUE -> 1.45f;
        };
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
        return copy(nativeModel,texture,attachments,nativeModel.getScale());
    }
    public static Optional<Projection> apply(Store<EntityStore> store,Ref<EntityStore> ref,EnemyDescriptor descriptor,EnemyVisualVariants registry){
        if(!store.isInThread())throw new IllegalStateException("ENEMY_PALETTE_WRONG_WORLD_THREAD");
        if(ref==null||!ref.isValid()||ref.getStore()!=store||!store.getExternalData().getWorld().getWorldConfig().getUuid().equals(descriptor.worldId()))
            throw new IllegalArgumentException("ENEMY_PALETTE_WORLD_BINDING");
        var id=store.getComponent(ref,UUIDComponent.getComponentType());var npc=store.getComponent(ref,NPCEntity.getComponentType());
        if(id==null||!id.getUuid().equals(descriptor.entityId())||npc==null||!npc.getRoleName().equals(descriptor.nativeRoleId()))
            throw new IllegalArgumentException("ENEMY_PALETTE_ACTOR_BINDING");
        var component=store.getComponent(ref,ModelComponent.getComponentType());var model=component==null?null:component.getModel();
        if(model==null)throw new IllegalStateException("ENEMY_PALETTE_MODEL_MISSING");
        var identity=store.getComponent(ref,EnemyActorIdentity.getComponentType());
        if(identity==null||!identity.state().equals(EnemyActorIdentity.State.of(descriptor)))
            throw new IllegalStateException("ENEMY_PALETTE_IDENTITY_MISSING");
        Model shown=model;
        if(descriptor.paletteId()!=null){
            var variant=registry.find(descriptor.paletteId()).orElseThrow(()->new IllegalStateException("ENEMY_FROZEN_PALETTE_MISSING"));
            if(!variant.canonicalRoleId().equals(descriptor.canonicalRoleId())
                    ||variant.superUniqueTemplateId()!=null&&(descriptor.templateBirth()==null||!variant.superUniqueTemplateId().equals(descriptor.templateBirth().templateId())))
                throw new IllegalStateException("ENEMY_PALETTE_MODEL_MISMATCH");
            shown=texturesOnly(shown,variant.textureOverrides());
        }
        var profile=MonsterVisualProfile.from(descriptor);
        if(profile.skinTint()!=null){
            var overrides=trorkOverrides(shown,profile);
            if(!overrides.isEmpty())shown=texturesOnly(shown,overrides);
            if(profile.armorVisualOwner()!=null){
                boolean applied=profile.armorVisualOwner()==EnemyAffixRegistry.Operator.STONE_SKIN
                        &&overrides.stream().anyMatch(o->o.originalTextureAssetId().contains("Attachments/Warrior/"));
                if(!applied){
                    String capability="ARMOR:"+descriptor.nativeRoleId()+":"+profile.armorVisualOwner();
                    if(CAPABILITY_LOGGED.add(capability))com.hypixel.hytale.logger.HytaleLogger.getLogger().atInfo().log(
                            "RPG_ENEMY_VISUAL_CAPABILITY role=%s armorOwner=%s separatedTint=false reason=UNSUPPORTED_ARMOR_REGION",
                            descriptor.nativeRoleId(),profile.armorVisualOwner());
                }
            }
        }
        if(profile.skinTint()!=null){
            if(identity.nativeVisualScale()==0)identity.captureNativeVisualScale(model.getScale());
            float baseline=(float)identity.nativeVisualScale();
            float target=baseline*rarityScale(descriptor.enemyRarity());
            if(Float.compare(model.getScale(),baseline)!=0&&Float.compare(model.getScale(),target)!=0)
                throw new IllegalStateException("ENEMY_ELITE_VISUAL_SCALE_BASELINE_MISMATCH");
            shown=visualScaleOnly(shown,baseline,descriptor.enemyRarity());
        }
        if(shown==model)return Optional.empty();
        store.replaceComponent(ref,ModelComponent.getComponentType(),new ModelComponent(shown));
        return Optional.of(new Projection(descriptor.worldId(),descriptor.entityId(),descriptor.encounterGeneration(),model,shown));
    }
    static List<EnemyVisualVariants.TextureOverride> trorkOverrides(Model model,MonsterVisualProfile profile){
        if(profile.skinTint()==null||!TRORK_MODEL.equals(model.getModelAssetId()))return List.of();
        var overrides=new ArrayList<EnemyVisualVariants.TextureOverride>();
        String nativeSkin=TRORK_BASE+"Model_Textures/Cyan_Dark.png";
        if(nativeSkin.equals(model.getTexture()))overrides.add(new EnemyVisualVariants.TextureOverride(
                TRORK_MODEL,nativeSkin,TRORK_VISUAL+"Skin_"+(switch(profile.rarity()){
                    case CHAMPION->"Champion";case UNIQUE->"Unique";case SUPER_UNIQUE->"SuperUnique";
                    default->throw new IllegalStateException("NON_PROMOTED_SKIN");
                })+".png"));
        if(profile.armorVisualOwner()==EnemyAffixRegistry.Operator.STONE_SKIN){
            var attachments=model.getAttachments();
            if(attachments!=null)for(String part:List.of("Chest","Hands","Feet","Head")){
                String nativeArmor=TRORK_BASE+"Attachments/Warrior/"+part+"_Texture.png";
                for(var attachment:attachments)if(nativeArmor.equals(attachment.getTexture())){
                    overrides.add(new EnemyVisualVariants.TextureOverride(TRORK_MODEL,nativeArmor,
                            TRORK_VISUAL+"Armor_StoneSkin_"+part+".png"));break;
                }
            }
        }
        return overrides;
    }
    public static void restore(Store<EntityStore> store,Ref<EntityStore> ref,Projection projection){
        if(!store.isInThread())throw new IllegalStateException("ENEMY_PALETTE_WRONG_WORLD_THREAD");
        if(ref==null||!ref.isValid()||ref.getStore()!=store||!projection.world().equals(store.getExternalData().getWorld().getWorldConfig().getUuid()))return;
        var id=store.getComponent(ref,UUIDComponent.getComponentType());var model=store.getComponent(ref,ModelComponent.getComponentType());
        if(id!=null&&projection.entity().equals(id.getUuid())&&model!=null&&model.getModel()==projection.shown())
            store.replaceComponent(ref,ModelComponent.getComponentType(),new ModelComponent(projection.previous()));
    }
}
