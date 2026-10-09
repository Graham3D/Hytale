package com.inigmasgames.hytalerpg.combat.damage;

import com.google.gson.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Authoritative cause aliases. Producer means the authored attack record, never its school or carrier. */
public final class DamageChannels {
    public enum Channel { PHYSICAL, WIND, WATER, FIRE, EARTH, LIGHTNING, VOID }
    public enum State { RESOLVED, UNRESOLVED_PRODUCER, UNMAPPED_NATIVE_CAUSE }
    public record Mapping(State state,Channel channel,String revision,String cause,String producer) {
        public Mapping { if((state==State.RESOLVED)!=(channel!=null))throw new IllegalArgumentException("CHANNEL_MAPPING_STATE"); }
    }
    private final String revision;
    private final Map<String,Channel> causes;
    private final Map<String,Map<String,Channel>> producers;
    public DamageChannels(JsonObject json) {
        exact(json,Set.of("schemaVersion","revision","nativeCauses","perProducerCauses"));
        if(!json.get("schemaVersion").isJsonPrimitive()||!json.getAsJsonPrimitive("schemaVersion").isNumber()
                ||!json.get("schemaVersion").getAsString().equals("1"))throw new IllegalArgumentException("CHANNEL_MAP_SCHEMA");
        revision=id(json.get("revision")); causes=entries(json.getAsJsonObject("nativeCauses"));
        var per=new TreeMap<String,Map<String,Channel>>();
        json.getAsJsonObject("perProducerCauses").entrySet().forEach(e->{
            if(causes.containsKey(e.getKey()))throw new IllegalArgumentException("AMBIGUOUS_NATIVE_CHANNEL:"+e.getKey());
            per.put(e.getKey(),entries(e.getValue().getAsJsonObject()));
        });
        producers=Collections.unmodifiableMap(per);
    }
    public String revision(){return revision;}
    public Mapping resolve(String cause,String authoredProducer) {
        Objects.requireNonNull(cause);
        var channel=causes.get(cause);
        if(channel!=null)return new Mapping(State.RESOLVED,channel,revision,cause,authoredProducer);
        var records=producers.get(cause);
        if(records==null)return new Mapping(State.UNMAPPED_NATIVE_CAUSE,null,revision,cause,authoredProducer);
        channel=authoredProducer==null?null:records.get(authoredProducer);
        return new Mapping(channel==null?State.UNRESOLVED_PRODUCER:State.RESOLVED,channel,revision,cause,authoredProducer);
    }
    /** Rewrite only approved per-record aliases before native mitigation, preserving all other cause semantics. */
    public String nativeCause(String cause,String authoredProducer) {
        if(!producers.containsKey(cause))return cause;
        var mapping=resolve(cause,authoredProducer);
        if(mapping.state()!=State.RESOLVED)return cause;
        return switch(mapping.channel()) {
            case PHYSICAL->"Physical";case WIND->"Wind";case WATER->"Water";case FIRE->"Fire";
            case EARTH->"Earth";case LIGHTNING->"Lightning";case VOID->"RPG_Void";
        };
    }
    private static Map<String,Channel> entries(JsonObject json){
        if(json.size()>256)throw new IllegalArgumentException("CHANNEL_MAP_BOUNDS");
        var result=new TreeMap<String,Channel>();json.entrySet().forEach(e->{
            if(e.getKey().isBlank())throw new IllegalArgumentException("CHANNEL_MAP_EMPTY_KEY");
            result.put(e.getKey(),Channel.valueOf(id(e.getValue())));
        });return Collections.unmodifiableMap(result);
    }
    private static String id(JsonElement value){
        if(value==null||!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isString()||value.getAsString().isBlank())
            throw new IllegalArgumentException("CHANNEL_MAP_STRING");return value.getAsString();
    }
    private static void exact(JsonObject json,Set<String> keys){if(!json.keySet().equals(keys))throw new IllegalArgumentException("CHANNEL_MAP_FIELDS");}
    private static final class Canonical {
        private static final DamageChannels INSTANCE=load();
        private static DamageChannels load(){
            try(var in=DamageChannels.class.getResourceAsStream("/rpg/balance/damage-channels-v1.json")){
                if(in==null)throw new IllegalStateException("CHANNEL_MAP_MISSING");
                return new DamageChannels(JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject());
            }catch(java.io.IOException e){throw new IllegalStateException(e);}
        }
    }
    public static DamageChannels canonical(){return Canonical.INSTANCE;}
}
