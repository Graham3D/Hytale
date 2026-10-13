package com.inigmasgames.hytalerpg.diagnostics.hro;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;

/** Offline selected native bindings; parses bytes without loading any class or executing archive content. */
final class HroFingerprint {
    private HroFingerprint() { }
    static void capture(Path archive,Path specification,Path output)throws IOException {
        JsonObject spec=HroOffline.object(specification);
        if(!HroOffline.value(spec,"schemaVersion").equals("1"))throw new IOException("BINDING_SCHEMA");
        JsonArray bindings=spec.getAsJsonArray("bindings");
        if(bindings==null||bindings.size()>256)throw new IOException("BINDING_COUNT_LIMIT");
        var rows=new ArrayList<Map<String,Object>>();Set<String> seen=new HashSet<>();
        try(var zip=new ZipFile(archive.toFile())) {
            for(JsonElement element:bindings) {
                JsonObject binding=element.getAsJsonObject();String name=HroOffline.required(binding,"entry");
                if(!seen.add(name)||name.contains("..")||name.startsWith("/")||name.contains("\\"))throw new IOException("INVALID_BINDING_PATH");
                String contract=HroOffline.required(binding,"contract"),owner=HroOffline.required(binding,"owner");
                var entry=zip.getEntry(name);var row=new LinkedHashMap<String,Object>();row.put("binding",name);row.put("contract",contract);row.put("migrationOwner",owner);
                row.put("retainedImplementation",HroContracts.ANCHORS.getOrDefault(contract,"UNKNOWN"));
                if(entry==null){row.put("status","CAPABILITY_BLOCKED_MISSING_BINDING");rows.add(row);continue;}
                if(entry.getSize()<0||entry.getSize()>2*1024*1024)throw new IOException("BINDING_SIZE_LIMIT");
                byte[] bytes;try(var in=zip.getInputStream(entry)){bytes=in.readNBytes(2*1024*1024+1);}
                if(bytes.length>2*1024*1024)throw new IOException("BINDING_DECOMPRESSION_LIMIT");
                row.put("rawHash",HroOffline.hashBytes(bytes));
                Object normalized;
                if(name.endsWith(".class"))normalized=signatures(bytes);
                else if(name.endsWith(".json"))normalized=ancestry(zip,name,HroOffline.parse(new String(bytes,StandardCharsets.UTF_8)),new HashSet<>(),0);
                else normalized=Base64.getEncoder().encodeToString(bytes);
                row.put("normalizedHash",HroOffline.hash(normalized));
                row.put("status","FINGERPRINTED_BEHAVIOR_UNPROVEN");
                row.put("normalization",name.endsWith(".class")?"PUBLIC_PROTECTED_DESCRIPTORS_ENUMS_AND_ANCESTRY":name.endsWith(".json")?"OBJECT_KEY_ORDER_ONLY; all fields/defaults and resolvable sibling Parent ancestry retained; no merge semantics invented":"RAW_BYTES");
                if(name.endsWith(".class"))row.put("signatures",normalized);
                if(name.endsWith(".json"))row.put("assetEvidence",normalized);
                rows.add(row);
            }
        }
        HroOffline.create(output,Map.of("schemaVersion",1,"archiveSha256",HroBundle.sha256(archive),"specificationSha256",HroBundle.sha256(specification),"bindings",rows,
                "limits","No behavior, codec-registration execution, effective inheritance merge or runtime publication inferred from names/hashes; unresolved parents remain gaps"));
    }
    private static Map<String,Object> ancestry(ZipFile zip,String name,JsonObject asset,Set<String> seen,int depth)throws IOException {
        if(depth>=8||!seen.add(name))return Map.of("entry",name,"gap","INHERITANCE_CYCLE_OR_DEPTH_LIMIT");
        var result=new LinkedHashMap<String,Object>();result.put("entry",name);result.put("fields",asset);
        String parent=HroOffline.value(asset,"Parent");
        if(parent.equals("UNKNOWN")){result.put("parent","NONE_OR_UNSUPPORTED_PARENT_FIELD");return result;}
        if(!parent.matches("[A-Za-z0-9_-]{1,192}")){result.put("parentGap","UNSUPPORTED_PARENT_REFERENCE");return result;}
        int slash=name.lastIndexOf('/');String parentPath=name.substring(0,slash+1)+parent+".json";
        var entry=zip.getEntry(parentPath);
        if(entry==null){result.put("parentGap","UNRESOLVED:"+parent);return result;}
        if(entry.getSize()<0||entry.getSize()>128*1024)throw new IOException("PARENT_ASSET_LIMIT");
        byte[] bytes;try(var in=zip.getInputStream(entry)){bytes=in.readNBytes(128*1024+1);}
        if(bytes.length>128*1024)throw new IOException("PARENT_ASSET_LIMIT");
        result.put("parent",ancestry(zip,parentPath,HroOffline.parse(new String(bytes,StandardCharsets.UTF_8)),seen,depth+1));
        return result;
    }
    static List<String> signatures(byte[] bytes)throws IOException {
        try(var in=new DataInputStream(new ByteArrayInputStream(bytes))) {
            if(in.readInt()!=0xcafebabe)throw new IOException("CLASS_MAGIC");in.readUnsignedShort();in.readUnsignedShort();
            int size=in.readUnsignedShort();String[] strings=new String[size];int[] names=new int[size];
            for(int i=1;i<size;i++)switch(in.readUnsignedByte()) {
                case 1 -> strings[i]=in.readUTF();
                case 7 -> names[i]=in.readUnsignedShort();
                case 8,16,19,20 -> in.skipNBytes(2);
                case 3,4,9,10,11,12,17,18 -> in.skipNBytes(4);
                case 5,6 -> {in.skipNBytes(8);i++;}
                case 15 -> in.skipNBytes(3);
                default -> throw new IOException("CLASS_CONSTANT_TAG");
            }
            var result=new ArrayList<String>();result.add("access:"+in.readUnsignedShort());
            for(String kind:List.of("class","super")){int index=in.readUnsignedShort();result.add(kind+":"+(index==0?"NONE":strings[names[index]]));}
            int interfaces=in.readUnsignedShort();for(int i=0;i<interfaces;i++)result.add("interface:"+strings[names[in.readUnsignedShort()]]);
            for(String kind:List.of("field","method")) {
                int count=in.readUnsignedShort();for(int i=0;i<count;i++) {
                    int flags=in.readUnsignedShort();String name=strings[in.readUnsignedShort()],descriptor=strings[in.readUnsignedShort()];
                    if((flags&5)!=0)result.add(kind+":"+flags+":"+name+":"+descriptor);
                    int attributes=in.readUnsignedShort();for(int j=0;j<attributes;j++){in.readUnsignedShort();long length=Integer.toUnsignedLong(in.readInt());if(length>bytes.length)throw new IOException("CLASS_ATTRIBUTE_LIMIT");in.skipNBytes(length);}
                }
            }
            Collections.sort(result);return List.copyOf(result);
        }catch(IndexOutOfBoundsException invalid){throw new IOException("INVALID_CLASS_REFERENCE",invalid);}
    }
    static void compare(Path previous,Path current,Path output)throws IOException {
        JsonObject a=HroOffline.object(previous),b=HroOffline.object(current);
        Map<String,JsonObject> old=index(a),next=index(b);var keys=new TreeSet<>(old.keySet());keys.addAll(next.keySet());
        var rows=new ArrayList<Map<String,Object>>();
        for(String key:keys) {
            JsonObject before=old.get(key),after=next.get(key);
            String status=before==null||after==null?"BINDING_ADDED_OR_REMOVED":!before.has("normalizedHash")||!after.has("normalizedHash")?"CAPABILITY_BLOCKED":
                    Objects.equals(before.get("normalizedHash"),after.get("normalizedHash"))?"NORMALIZED_UNCHANGED_BEHAVIOR_UNPROVEN":"ADAPT_NATIVE_BOUNDARY";
            rows.add(Map.of("binding",key,"status",status,"old",before==null?new JsonObject():before,"new",after==null?new JsonObject():after));
        }
        HroOffline.create(output,Map.of("schemaVersion",1,"bindings",rows,"cause","UNDETERMINED"));
    }
    private static Map<String,JsonObject> index(JsonObject object)throws IOException {
        if(!HroOffline.value(object,"schemaVersion").equals("1")||!object.has("bindings")||object.getAsJsonArray("bindings").size()>256)throw new IOException("FINGERPRINT_SCHEMA_OR_LIMIT");
        var result=new TreeMap<String,JsonObject>();for(var entry:object.getAsJsonArray("bindings")) {
            JsonObject row=entry.getAsJsonObject();if(result.put(HroOffline.required(row,"binding"),row)!=null)throw new IOException("DUPLICATE_BINDING");
        }return result;
    }
}
