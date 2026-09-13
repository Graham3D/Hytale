package com.inigmasgames.hytalerpg.input;

import com.hypixel.hytale.server.core.asset.type.particle.config.*;
import com.hypixel.hytale.server.core.asset.type.model.config.*;

/** Exact installed child appearance and derivative placement contract; not a rendering certificate. */
public final class NativeHealingParticlePathAudit {
    public static void requireAssets(){
        requireWorldAssets();
        for(String child:java.util.List.of("Sparks","Glow","Plus")){
            var stock=ParticleSpawner.getAssetMap().getAsset("Beam_Heal_Green2_"+child);
            var derived=ParticleSpawner.getAssetMap().getAsset("RPG_Heal_Path_"+child);
            if(stock==null||derived==null||!stock.getParticle().toPacket().equals(derived.getParticle().toPacket())
                    ||stock.getRenderMode()!=derived.getRenderMode()||stock.getParticleRotationInfluence()!=derived.getParticleRotationInfluence()
                    ||stock.isLinearFiltering()!=derived.isLinearFiltering()||!stock.getParticleLifeSpan().equals(derived.getParticleLifeSpan()))throw new IllegalStateException("HEAL_PATH_STOCK_APPEARANCE:"+child);
            var p=derived.toPacket();
            if(p.initialVelocity==null||p.initialVelocity.speed.min!=0||p.initialVelocity.speed.max!=0||p.trailSpawnerPositionMultiplier!=0||p.trailSpawnerRotationMultiplier!=0)
                throw new IllegalStateException("HEAL_PATH_AUTONOMOUS_MOTION:"+child);
        }
        for(String kind:java.util.List.of("Blips","Pulse")){
            String id="RPG_Heal_Path_"+kind;var system=ParticleSystem.getAssetMap().getAsset(id);var asset=ModelAsset.getAssetMap().getAsset(id);
            if(system==null||asset==null)throw new IllegalStateException("HEAL_PATH_ASSET_MISSING:"+id);
            if(system.getSpawners().length!=(kind.equals("Blips")?1:2))throw new IllegalStateException("HEAL_PATH_LAYER_COUNT");
            for(var group:system.getSpawners())if(group.getInitialVelocity()!=null||group.getTotalSpawners()!=1||group.getMaxConcurrent()!=1)throw new IllegalStateException("HEAL_PATH_GROUP_MOTION");
            var p=Model.createStaticScaledModel(asset,1).toPacket().particles;
            if(p==null||p.length!=1||!id.equals(p[0].systemId)||p[0].scale!=1||p[0].detachedFromModel||!p[0].clearParticlesOnRemove)throw new IllegalStateException("HEAL_PATH_MODEL_ATTACHMENT");
        }
        com.hypixel.hytale.logger.HytaleLogger.getLogger().atInfo().log("RPG_HEAL_PARTICLE_PATH_ASSETS revision=R032-AM result=PASS stockParticleAppearanceExact=true stockLifespans=true zeroVelocity=true anchorFollow=true layerSystems=2 noNewTextures=true connectedProof=false");
    }
    private NativeHealingParticlePathAudit(){}
    private static void requireWorldAssets(){
        for(String child:java.util.List.of("Sparks","Glow","Plus")){
            var original=ParticleSpawner.getAssetMap().getAsset("RPG_Heal_Path_"+child);
            var asset=ParticleSpawner.getAssetMap().getAsset("RPG_Heal_World_"+child);
            if(original==null||asset==null||!original.getParticle().toPacket().equals(asset.getParticle().toPacket())
                    ||original.getRenderMode()!=asset.getRenderMode()||original.getParticleRotationInfluence()!=asset.getParticleRotationInfluence()
                    ||original.isLinearFiltering()!=asset.isLinearFiltering())throw new IllegalStateException("HEAL_WORLD_APPEARANCE:"+child);
            var p=asset.toPacket();
            if(!p.spawnBurst||p.totalParticles.min!=1||p.totalParticles.max!=1||p.maxConcurrentParticles!=1
                    ||p.particleLifeSpan.min!=.18f||p.particleLifeSpan.max!=.18f||p.lifeSpan!=.18f
                    ||p.initialVelocity.speed.min!=0||p.initialVelocity.speed.max!=0||p.waveDelay.min!=0||p.waveDelay.max!=0)
                throw new IllegalStateException("HEAL_WORLD_BURST:"+child);
        }
        for(String kind:java.util.List.of("Blips","Pulse")){
            var asset=ParticleSystem.getAssetMap().getAsset("RPG_Heal_World_"+kind);
            if(asset==null||asset.getLifeSpan()!=.18f||asset.getCullDistance()!=30||asset.getSpawners().length!=(kind.equals("Blips")?1:2))
                throw new IllegalStateException("HEAL_WORLD_SYSTEM:"+kind);
        }
        com.hypixel.hytale.logger.HytaleLogger.getLogger().atInfo().log("RPG_HEAL_WORLD_PARTICLE_ASSETS revision=R032-AO result=PASS appearanceExact=true immediateBurst=true finiteSeconds=0.18 zeroVelocity=true connectedProof=false");
    }
}
