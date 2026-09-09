import com.google.gson.*;
import java.nio.file.*;
import java.io.*;
import java.util.*;
import java.util.zip.*;

/** Offline pinned-asset audit. No item-name tier inference; follows authored tags/templates and selected basic attack variables. */
class AuditVanillaWeaponPower {
    record Asset(String path,JsonObject data){}
    record Value(double power,String asset,String property){}
    static final Map<String,Asset> items=new TreeMap<>(), roots=new HashMap<>(), interactions=new HashMap<>(),projectiles=new HashMap<>(),configs=new HashMap<>();
    static final Map<String,String> families=Map.ofEntries(Map.entry("Sword","SWORD"),Map.entry("Longsword","LONGSWORD"),Map.entry("Dagger","DAGGER"),Map.entry("Daggers","DAGGER"),Map.entry("Mace","MACE"),Map.entry("Axe","BATTLEAXE"),Map.entry("Battleaxe","BATTLEAXE"),Map.entry("Spear","SPEAR"),Map.entry("Bow","BOW"),Map.entry("Crossbow","CROSSBOW"),Map.entry("Staff","STAFF"),Map.entry("Wand","WAND"),Map.entry("Spellbook","SPELLBOOK"),Map.entry("Shield","SHIELD"),Map.entry("Bomb","BOMB"),Map.entry("Gun","GUN"));
    static final Map<String,List<String>> selectors=Map.ofEntries(
        Map.entry("SWORD",List.of("Swing_Left_Damage","Sword_Swing_Left_Fast_Damage","Longsword_Swing_Left_Damage")),
        Map.entry("LONGSWORD",List.of("Longsword_Swing_Left_Damage")),Map.entry("DAGGER",List.of("Swing_Left_Damage","Dagger_Swing_Left_Damage","Daggers_Swing_Left_Damage","Daggers_Swing_Left_Right_Damage")),
        Map.entry("MACE",List.of("Swing_Left_Damage","Mace_Swing_Left_Damage")),Map.entry("BATTLEAXE",List.of("Swing_Down_Left_Damage","Battleaxe_Swing_Left_Damage","Axe_Swing_Down_Left_Damage")),
        Map.entry("SPEAR",List.of("Spear_Stab_Damage","Spear_Swing_Left_Damage","Spear_Spin_Swing_Left_Damage")),Map.entry("BOW",List.of("Primary_Shoot_Damage_Strength_0")),
        Map.entry("CROSSBOW",List.of("Standard_Projectile_Damage")),Map.entry("STAFF",List.of("Fireball_Impact_0")),
        Map.entry("GUN",List.of("Gun_Shoot","Burn_Damage")),Map.entry("BOMB",List.of("Item_Throw_Projectile")));
    static JsonObject merge(JsonObject parent,JsonObject own){var r=parent.deepCopy();for(var e:own.entrySet()){if(e.getValue().isJsonObject()&&r.has(e.getKey())&&r.get(e.getKey()).isJsonObject())r.add(e.getKey(),merge(r.getAsJsonObject(e.getKey()),e.getValue().getAsJsonObject()));else r.add(e.getKey(),e.getValue().deepCopy());}return r;}
    static JsonObject effective(Asset a,int depth){if(a==null||depth>24)throw new IllegalArgumentException("ITEM_PARENT_UNRESOLVED");String parent=string(a.data,"Parent");return parent.isEmpty()?a.data:merge(effective(items.get(parent),depth+1),a.data);}
    static String string(JsonObject o,String k){return o!=null&&o.has(k)&&o.get(k).isJsonPrimitive()?o.get(k).getAsString():"";}
    static String templateKind(Asset a,int depth){if(a==null||depth>24)return "";String kind=families.getOrDefault(string(a.data,"PlayerAnimationsId"),"");return kind.isEmpty()?templateKind(items.get(string(a.data,"Parent")),depth+1):kind;}
    static Value variable(Asset a,String key,int depth){if(a==null||depth>24)return null;var vars=a.data.getAsJsonObject("InteractionVars");if(vars!=null&&vars.has(key))return damage(vars.get(key),a.path,"InteractionVars/"+key,0);return variable(items.get(string(a.data,"Parent")),key,depth+1);}
    static Value damage(JsonElement e,String asset,String pointer,int depth){
        if(e==null||depth>24)return null;
        if(e.isJsonPrimitive()) {var ref=asset.startsWith("Server/Item/RootInteractions/")?interactions.get(e.getAsString()):roots.get(e.getAsString());if(ref==null)ref=interactions.get(e.getAsString());return ref==null?null:damage(ref.data,ref.path,"",depth+1);}
        if(!e.isJsonObject())return null;var o=e.getAsJsonObject();
        var calc=o.getAsJsonObject("DamageCalculator");
        if(calc!=null&&calc.has("BaseDamage")){if(!Set.of("","Absolute").contains(string(calc,"Type")))return null;var b=calc.getAsJsonObject("BaseDamage");if(b.size()==1){var part=b.entrySet().iterator().next();double n=part.getValue().getAsDouble();if(Double.isFinite(n)&&n>0)return new Value(n,asset,join(pointer,"DamageCalculator/BaseDamage/"+part.getKey()));}}
        if(o.has("ProjectileId")){var p=projectiles.get(string(o,"ProjectileId"));if(p!=null&&p.data.has("Damage"))return new Value(p.data.get("Damage").getAsDouble(),p.path,"Damage");}
        var config=o.get("Config");if(config!=null&&config.isJsonObject()&&config.getAsJsonObject().has("EntityDamage")){double n=config.getAsJsonObject().get("EntityDamage").getAsDouble();if(n>0&&Double.isFinite(n))return new Value(n,asset,join(pointer,"Config/EntityDamage"));}
        if(config!=null&&config.isJsonPrimitive()){var c=configs.get(config.getAsString());if(c!=null)return configDamage(c,depth+1);}
        if(o.has("Interactions")&&o.get("Interactions").isJsonArray()){var list=o.getAsJsonArray("Interactions");Value found=null;for(int i=0;i<list.size();i++){var v=damage(list.get(i),asset,join(pointer,"Interactions/"+i),depth+1);if(v!=null){if(found!=null)return null;found=v;}}if(found!=null)return found;}
        for(String branch:List.of("HitEntity","Next"))if(o.has(branch)&&!string(o,"Type").equals("Charging")){var v=damage(o.get(branch),asset,join(pointer,branch),depth+1);if(v!=null)return v;}
        String parent=string(o,"Parent");var ref=interactions.get(parent);if(ref==null)ref=roots.get(parent);return ref==null?null:damage(ref.data,ref.path,"",depth+1);
    }
    static Value configDamage(Asset c,int depth){if(depth>24)return null;var events=c.data.getAsJsonObject("Interactions");if(events!=null){String event=events.has("ProjectileHit")?"ProjectileHit":"ProjectileSpawn";if(events.has(event))return damage(events.get(event),c.path,"Interactions/"+event,depth+1);}return configDamageParent(c,depth+1);}
    static Value configDamageParent(Asset c,int depth){var p=configs.get(string(c.data,"Parent"));return p==null?null:configDamage(p,depth);}
    static String join(String prefix,String suffix){return prefix.isEmpty()?suffix:prefix+"/"+suffix;}
    public static void main(String[] args)throws Exception{
        var zipPath=Path.of(args[0]);var destination=Path.of(args[1]);var auditPath=Path.of(args[2]);
        var digest=java.security.MessageDigest.getInstance("SHA-256");
        try(var input=new java.security.DigestInputStream(Files.newInputStream(zipPath),digest)){input.transferTo(OutputStream.nullOutputStream());}
        if(!HexFormat.of().formatHex(digest.digest()).equalsIgnoreCase("46F6AA12DECF4F900FCFDF28ECC67568403C37A3AD0A03A4E4CF7653A324AE39"))throw new IllegalStateException("INSTALLED_ASSET_VERSION_CHANGED_REAUDIT_REQUIRED");
        try(var zip=new ZipFile(zipPath.toFile())){for(var e:Collections.list(zip.entries())){
            String p=e.getName();Map<String,Asset> map=p.startsWith("Server/Item/Items/")?items:p.startsWith("Server/Item/RootInteractions/")?roots:p.startsWith("Server/Item/Interactions/")?interactions:p.startsWith("Server/Projectiles/")?projectiles:p.startsWith("Server/ProjectileConfigs/")?configs:null;
            if(map==null||!p.endsWith(".json"))continue;
            try(var r=new InputStreamReader(zip.getInputStream(e),java.nio.charset.StandardCharsets.UTF_8)){var json=JsonParser.parseReader(r);if(json.isJsonObject()){String id=p.substring(p.lastIndexOf('/')+1,p.length()-5);if(map.containsKey(id)){if(map!=configs)throw new IllegalStateException("DUPLICATE_ASSET_ID "+id);map.put(id,null);System.out.println("AMBIGUOUS_CONFIG_EXCLUDED "+id);}else map.put(id,new Asset(p,json.getAsJsonObject()));}}
        }}
        var records=new ArrayList<Map<String,Object>>();var audit=new ArrayList<Map<String,Object>>();
        for(var entry:items.entrySet()){
            var a=entry.getValue();
            JsonObject item=effective(a,0),tags=item.getAsJsonObject("Tags");String family="";boolean weapon=false;
            if(tags!=null){var t=tags.getAsJsonArray("Type");weapon=t!=null&&t.toString().contains("\"Weapon\"");var f=tags.getAsJsonArray("Family");if(f!=null&&f.size()==1)family=f.get(0).getAsString();}
            if(!weapon&&!a.path.startsWith("Server/Item/Items/Weapon/"))continue;
            String kind=families.getOrDefault(family,"");
            // Audited template animation ID supplies absent native Family, never a filename guess.
            if(kind.isEmpty()&&family.isEmpty())kind=templateKind(a,0);
            if(family.equals("Magic")&&string(item,"PlayerAnimationsId").equals("Staff"))kind="STAFF";
            String reason="",policy="NATIVE_UNCHARGED_BASE";Value value=null;
            if(!weapon||kind.isEmpty())reason="NOT_SUPPORTED_RPG_WEAPON_FAMILY";
            else {
                for(String selector:selectors.getOrDefault(kind,List.of())){value=variable(a,selector,0);if(value!=null)break;}
                if(value==null&&kind.equals("BOMB")&&item.has("Interactions"))value=damage(item.getAsJsonObject("Interactions").get("Primary"),a.path,"Interactions/Primary",0);
                if(value==null&&Set.of("STAFF","WAND","SPELLBOOK","SHIELD").contains(kind)){
                    value=new Value(20,a.path,"");policy="RPG_AUTHORED_BASE";
                }
                if(value==null)reason="UNCHARGED_SOURCE_UNRESOLVED";
            }
            var row=new LinkedHashMap<String,Object>();row.put("itemId",entry.getKey());row.put("asset",a.path);row.put("family",family);row.put("kind",kind);row.put("animation",string(item,"PlayerAnimationsId"));row.put("parent",string(a.data,"Parent"));row.put("variables",item.has("InteractionVars")?item.getAsJsonObject("InteractionVars").keySet():List.of());row.put("result",reason.isEmpty()?"RESOLVED":reason);
            if(value!=null){var record=new LinkedHashMap<String,Object>();record.put("itemId",entry.getKey());record.put("kind",kind);record.put("nativeFamily",family);record.put("sourceAsset",value.asset);record.put("sourceProperty",value.property);record.put("selectionPolicy",policy);record.put("basePower",value.power);records.add(record);row.put("resolution",record);}
            audit.add(row);
        }
        var gson=new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
        Files.createDirectories(destination.toAbsolutePath().getParent());Files.createDirectories(auditPath.toAbsolutePath().getParent());
        Files.writeString(destination,gson.toJson(Map.of("schemaVersion",1,"registryId","rpg.native-item-power.vanilla-0.7-pre1","policy","Pinned native uncharged family selectors; STAFF/WAND/SPELLBOOK/SHIELD lacking an uncharged spell/shield base use the existing explicit RPG reference base 20. No name or charged/combo averaging.","items",records))+"\n");
        Files.writeString(auditPath,gson.toJson(audit)+"\n");
        System.out.println("Audited="+audit.size()+" resolved="+records.size());
        for(var r:audit)if(!r.get("result").equals("RESOLVED"))System.out.println(r.get("result")+" "+r.get("itemId")+" family="+r.get("family")+" kind="+r.get("kind")+" vars="+r.get("variables"));
    }
}
