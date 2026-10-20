package com.inigmasgames.hytalerpg.execution.hytale;

import com.google.gson.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** The receipt adapter assumes one original managed damage leaf per authored native arrow. */
class NativePlayerGearProjectileAssetTest {
    private static final Path CONFIGS=Path.of("src/main/resources/Server/ProjectileConfigs/RPG/Gear");
    private static final Path INTERACTIONS=Path.of("src/main/resources/Server/Item/Interactions/RPG/Gear");

    @Test void everyManagedArrowHitHasOneDamageLeafAndNativeDespawn() throws IOException {
        try(var files=Files.list(CONFIGS)){
            var configs=files.filter(path->path.getFileName().toString().endsWith(".json")).sorted().toList();
            assertEquals(19,configs.size(),"Reaudit newly added managed projectile configs");
            for(var path:configs){
                var hit=JsonParser.parseString(Files.readString(path)).getAsJsonObject()
                        .getAsJsonObject("Interactions").getAsJsonObject("ProjectileHit")
                        .getAsJsonArray("Interactions");
                assertEquals(Set.of(1),counts(hit,new HashSet<>()),path.toString());
                assertEquals("RPG_GearRoute_I_Common_Projectile_Despawn",
                        hit.get(hit.size()-1).getAsString(),path.toString());
            }
        }
    }

    private static Set<Integer> counts(JsonElement value,Set<String> visiting) throws IOException {
        if(value==null||value.isJsonNull())return Set.of(0);
        if(value.isJsonPrimitive()){
            String id=value.getAsString();Path file=INTERACTIONS.resolve(id+".json");
            if(!Files.isRegularFile(file))return Set.of(0);
            assertTrue(visiting.add(id),"Cycle in native projectile damage graph: "+id);
            try{return counts(JsonParser.parseString(Files.readString(file)),visiting);}
            finally{visiting.remove(id);}
        }
        if(value.isJsonArray()){
            Set<Integer> total=Set.of(0);
            for(var child:value.getAsJsonArray()){
                var next=counts(child,visiting);var combined=new HashSet<Integer>();
                for(int a:total)for(int b:next)combined.add(a+b);
                total=Set.copyOf(combined);
            }
            return total;
        }
        var object=value.getAsJsonObject();
        String type=object.has("Type")?object.get("Type").getAsString():"";
        if(type.equals("EffectCondition")){
            var choices=new HashSet<Integer>(counts(object.get("Next"),visiting));
            choices.addAll(counts(object.get("Failed"),visiting));return Set.copyOf(choices);
        }
        if(type.equals("Replace"))return counts(object.getAsJsonObject("DefaultValue"),visiting);
        int own=type.equals("RPG_GearDamage")?1:0;
        if(own==1){
            var base=object.getAsJsonObject("DamageCalculator").getAsJsonObject("BaseDamage");
            assertEquals(1,base.size(),"Native arrow must retain one damage channel");
            assertTrue(base.has("Physical")||base.has("Projectile"));
        }
        var sequence=new JsonArray();
        if(object.has("Interactions"))for(var child:object.getAsJsonArray("Interactions"))sequence.add(child);
        if(object.has("Next"))sequence.add(object.get("Next"));
        var suffix=counts(sequence,visiting);var result=new HashSet<Integer>();
        for(int count:suffix)result.add(own+count);
        return Set.copyOf(result);
    }
}
