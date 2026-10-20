package com.inigmasgames.hytalerpg.enemies;

import com.google.gson.JsonObject;
import com.inigmasgames.hytalerpg.gear.GearRandom;
import java.util.*;
import static com.inigmasgames.hytalerpg.enemies.EnemyAffixRegistry.*;

/** Texture-only authoring. No model, scale, animation, collision, role or navigation overrides. */
public final class EnemyVisualVariants {
    public record TextureOverride(String modelAssetId,String originalTextureAssetId,String replacementTextureAssetId){
        public TextureOverride {identifier(modelAssetId);textureReference(originalTextureAssetId);textureReference(replacementTextureAssetId);}
    }
    public record Variant(String id,String canonicalRoleId,EnemyRarity enemyRarity,int weight,List<TextureOverride> textureOverrides,String superUniqueTemplateId){
        public Variant {
            identifier(id);identifier(canonicalRoleId);Objects.requireNonNull(enemyRarity);
            require(weight>0,"VISUAL_VARIANT_WEIGHT");textureOverrides=List.copyOf(textureOverrides);
            require(!textureOverrides.isEmpty()&&textureOverrides.size()<=64,"VISUAL_OVERRIDE_BUDGET");
            var keys=new HashSet<List<String>>();for(var override:textureOverrides)
                require(keys.add(List.of(override.modelAssetId(),override.originalTextureAssetId())),"DUPLICATE_TEXTURE_OVERRIDE");
            if(superUniqueTemplateId!=null){identifier(superUniqueTemplateId);require(enemyRarity==EnemyRarity.SUPER_UNIQUE,"VISUAL_TEMPLATE_RARITY");}
        }
    }
    private final String revision;private final Map<String,Variant> variants;
    public EnemyVisualVariants(JsonObject json){
        exact(json,Set.of("schemaVersion","revision","selectionOrder","runtimeTint","variants"));
        require(number(json.get("schemaVersion"),true,1)==1,"VISUAL_VARIANTS_SCHEMA");revision=string(json,"revision");
        require(strings(json.get("selectionOrder")).equals(List.of("SUPER_UNIQUE_OVERRIDE","EXACT_ROLE_RARITY_VARIANT","CERTIFIED_RUNTIME_TINT","NATIVE_TEXTURE")),"VISUAL_SELECTION_ORDER");
        var tint=json.getAsJsonObject("runtimeTint");exact(tint,Set.of("enabled","requiresCertifiedInstalledAdapter"));
        for(String key:tint.keySet())require(tint.get(key).isJsonPrimitive()&&tint.getAsJsonPrimitive(key).isBoolean(),"VISUAL_TINT_BOOLEAN");
        require(!tint.get("enabled").getAsBoolean()&&tint.get("requiresCertifiedInstalledAdapter").getAsBoolean(),"RUNTIME_TINT_NOT_CERTIFIED");
        var result=new TreeMap<String,Variant>();for(var value:json.getAsJsonArray("variants")){
            var v=value.getAsJsonObject();var fields=new HashSet<>(Set.of("id","canonicalRoleId","enemyRarity","weight","textureOverrides"));
            if(v.has("superUniqueTemplateId"))fields.add("superUniqueTemplateId");exact(v,fields);
            var overrides=new ArrayList<TextureOverride>();for(var entry:v.getAsJsonArray("textureOverrides")){
                var o=entry.getAsJsonObject();exact(o,Set.of("modelAssetId","originalTextureAssetId","replacementTextureAssetId"));
                overrides.add(new TextureOverride(string(o,"modelAssetId"),string(o,"originalTextureAssetId"),string(o,"replacementTextureAssetId")));
            }
            var variant=new Variant(string(v,"id"),string(v,"canonicalRoleId"),EnemyRarity.valueOf(string(v,"enemyRarity")),
                    (int)number(v.get("weight"),true,Integer.MAX_VALUE),overrides,v.has("superUniqueTemplateId")?string(v,"superUniqueTemplateId"):null);
            require(result.putIfAbsent(variant.id(),variant)==null,"DUPLICATE_VISUAL_VARIANT");
        }
        require(result.size()<=4096,"VISUAL_VARIANTS_BUDGET");variants=Collections.unmodifiableMap(result);
    }
    public String revision(){return revision;}
    public Collection<Variant> all(){return variants.values();}
    public Optional<Variant> find(String id){return Optional.ofNullable(variants.get(id));}
    public Optional<Variant> select(String role,EnemyRarity rarity,String explicitOverride,String frozenBirthSeed){
        return select(role,rarity,null,explicitOverride,frozenBirthSeed);
    }
    public Optional<Variant> select(String role,EnemyRarity rarity,String templateId,String explicitOverride,String frozenBirthSeed){
        if(explicitOverride!=null){
            require(rarity==EnemyRarity.SUPER_UNIQUE,"VISUAL_OVERRIDE_REQUIRES_SUPER_UNIQUE");
            var chosen=find(explicitOverride).orElseThrow(()->new IllegalArgumentException("MISSING_VISUAL_OVERRIDE:"+explicitOverride));
            require(chosen.canonicalRoleId().equals(role),"VISUAL_OVERRIDE_ROLE_MISMATCH");
            require(chosen.superUniqueTemplateId()==null||chosen.superUniqueTemplateId().equals(templateId),"VISUAL_OVERRIDE_TEMPLATE_MISMATCH");return Optional.of(chosen);
        }
        var candidates=variants.values().stream().filter(v->v.canonicalRoleId().equals(role)&&v.enemyRarity()==rarity
                &&(v.superUniqueTemplateId()==null||v.superUniqueTemplateId().equals(templateId))).toList();
        if(candidates.isEmpty())return Optional.empty();
        long total=candidates.stream().mapToLong(Variant::weight).sum();
        long draw=new GearRandom(new com.google.gson.Gson().toJson(List.of(revision,frozenBirthSeed))).stream("me.palette").nextLong(total);
        for(var candidate:candidates){if(draw<candidate.weight())return Optional.of(candidate);draw-=candidate.weight();}
        throw new IllegalStateException("VISUAL_DRAW_RANGE");
    }
    public void validateBindings(java.util.function.Predicate<Variant> exactRoleModelBinding,java.util.function.Predicate<String> packagedTexture){
        for(var variant:variants.values()){
            require(exactRoleModelBinding.test(variant),"VISUAL_NATIVE_BINDING_MISMATCH:"+variant.id());
            for(var override:variant.textureOverrides())require(packagedTexture.test(override.originalTextureAssetId())&&packagedTexture.test(override.replacementTextureAssetId()),"VISUAL_PACKAGED_TEXTURE_MISSING:"+variant.id());
        }
    }
    private static void identifier(String value){require(value!=null&&!value.isBlank()&&value.length()<=256&&value.chars().noneMatch(Character::isISOControl),"VISUAL_VARIANT_ID");}
    public static void textureReference(String value){
        require(value!=null&&!value.isBlank()&&value.length()<=512&&!value.startsWith("/")&&!value.contains("\\")&&!value.contains(":")
                &&value.endsWith(".png")&&Arrays.stream(value.split("/",-1)).noneMatch(s->s.isBlank()||s.equals(".")||s.equals(".."))
                &&value.chars().noneMatch(Character::isISOControl),"INVALID_PACKAGED_TEXTURE_PATH");
    }
    public static EnemyVisualVariants canonical(){return new EnemyVisualVariants(resource("visual-variants-v1.json"));}
}
