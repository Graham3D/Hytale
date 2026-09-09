package com.inigmasgames.hytalerpg.combat.power;

import com.google.gson.Gson;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Explicit RPG-selected production bases. Native min/max damage summaries never authorize power. */
public final class NativeItemPowerRegistry {
    public record Entry(String itemId,String kind,String nativeFamily,String sourceAsset,String sourceProperty,
                        String selectionPolicy,double basePower) {
        public Entry {
            if(itemId==null||itemId.isBlank()||kind==null||nativeFamily==null||sourceAsset==null
                    ||sourceProperty==null||selectionPolicy==null||!Double.isFinite(basePower)||basePower<=0)
                throw new IllegalArgumentException("Invalid audited native power record");
            if(!Set.of("NATIVE_UNCHARGED_BASE","RPG_AUTHORED_BASE").contains(selectionPolicy))
                throw new IllegalArgumentException("Unknown native power selection policy");
        }
        public boolean magic(){return Set.of("STAFF","WAND","SPELLBOOK").contains(kind);}
        public ItemPowerDescriptor descriptor(){
            String tag=magic()?"RPG_WEAPON_MAGIC":Set.of("LONGSWORD","MACE","BATTLEAXE","SPEAR","SHIELD").contains(kind)?"RPG_WEAPON_HEAVY":"RPG_WEAPON_LIGHT";
            return new ItemPowerDescriptor(itemId,Set.of(tag),magic()?null:basePower,magic()?basePower:null);
        }
    }
    private final Map<String,Entry> entries;
    public NativeItemPowerRegistry(List<Entry> entries){
        var indexed=new LinkedHashMap<String,Entry>();
        for(var e:entries)if(indexed.put(e.itemId(),e)!=null)throw new IllegalArgumentException("Duplicate audited item power");
        this.entries=Map.copyOf(indexed);
    }
    public static NativeItemPowerRegistry loadCanonical(){
        try(var stream=NativeItemPowerRegistry.class.getResourceAsStream("/rpg/runtime/native-item-power-r032.json")){
            if(stream==null)throw new IllegalStateException("Native item power registry missing");
            var data=new Gson().fromJson(new InputStreamReader(stream,StandardCharsets.UTF_8),Data.class);
            if(data.schemaVersion()!=1||!data.registryId().equals("rpg.native-item-power.r032"))throw new IllegalStateException("Unsupported native item registry");
            return new NativeItemPowerRegistry(data.items());
        }catch(java.io.IOException e){throw new IllegalStateException("Native item power registry unreadable",e);}
    }
    public Optional<Entry> find(String id){return Optional.ofNullable(entries.get(id));}
    public Collection<Entry> all(){return entries.values();}
    /** Empty expected Family is an audited absence (Battleaxe Iron), never a wildcard. Type must be Weapon. */
    public Optional<Entry> resolve(String id,Map<String,String[]> tags){
        var e=entries.get(id);var family=tags==null?null:tags.get("Family");
        var type=tags==null?null:tags.get("Type");
        if(e==null||type==null||!Arrays.asList(type).contains("Weapon"))return Optional.empty();
        boolean matches=e.nativeFamily().isEmpty()?family==null||family.length==0:
                family!=null&&family.length==1&&e.nativeFamily().equals(family[0]);
        return matches?Optional.of(e):Optional.empty();
    }
    private record Data(int schemaVersion,String registryId,List<Entry> items){}
}
