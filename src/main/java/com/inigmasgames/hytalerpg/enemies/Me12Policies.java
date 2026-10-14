package com.inigmasgames.hytalerpg.enemies;

import com.google.gson.*;
import com.inigmasgames.hytalerpg.progress.RewardIntent;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static com.inigmasgames.hytalerpg.enemies.EnemyAffixRegistry.require;

/** An indivisible, immutable set of effective definitions. Archive the exact UTF-8 bytes, not a reserialization. */
public record Me12Policies(List<Name> prefixes,List<Name> suffixes,List<Document> documents,String digest) {
    public static final List<String> ASSETS=List.of("name-pools-v2.json","selection-policy-v2.json","reward-policy-v2.json",
            "monster-affixes-v1.json","master-enemies-v1.json","affinities-v1.json");
    public record Name(String id,String text,int tier,int nameWeight,String signature,Set<String> fillers) {
        public Name {
            require(id!=null&&id.matches("[a-z][a-z0-9_-]{0,63}")&&text!=null&&!text.isBlank()&&text.length()<=128,"ME12_NAME_ID");
            require(tier>=1&&tier<=3&&nameWeight>0&&nameWeight<=100000,"ME12_NAME_WEIGHT_TIER");
            affix(signature);fillers=Collections.unmodifiableSet(new TreeSet<>(fillers));fillers.forEach(Me12Policies::affix);
            require(!fillers.contains(signature)&& (tier!=1||fillers.isEmpty()),"ME12_NAME_FILLER");
        }
    }
    public record Reference(String asset,String revision,String digest) {
        public Reference {require(ASSETS.contains(asset)&&revision!=null&&!revision.isBlank()&&revision.length()<=128&&digest!=null&&digest.matches("[a-f0-9]{64}"),"ME12_POLICY_REFERENCE");}
    }
    public record Document(Reference reference,String utf8) {
        public Document {Objects.requireNonNull(reference);Objects.requireNonNull(utf8,"ME12_POLICY_BYTES");require(utf8.length()<=131072&&RewardIntent.digest(utf8).equals(reference.digest()),"ME12_POLICY_DIGEST");
            var object=strictObject(utf8);require(string(object,"revision").equals(reference.revision()),"ME12_POLICY_REVISION");validateContent(reference.asset(),object);}
    }
    public Me12Policies {
        prefixes=List.copyOf(prefixes);suffixes=List.copyOf(suffixes);documents=List.copyOf(documents);
        require(documents.size()==ASSETS.size()&&documents.stream().map(d->d.reference().asset()).toList().equals(ASSETS),"ME12_POLICY_DOCUMENT_SET");
        require(digest.equals(bundleDigest(documents)),"ME12_POLICY_BUNDLE_DIGEST");
        var authored=JsonParser.parseString(documents.getFirst().utf8()).getAsJsonObject();
        require(prefixes.equals(names(authored.getAsJsonArray("prefixes")))&&suffixes.equals(names(authored.getAsJsonArray("suffixes"))),"ME12_POLICY_NAME_CONTENT");
    }
    public static Me12Policies canonical(){var bytes=new LinkedHashMap<String,String>();ASSETS.forEach(a->bytes.put(a,resource(a)));return load(bytes);}
    public static Me12Policies load(Map<String,String> bytes) {
        require(bytes.keySet().equals(new HashSet<>(ASSETS)),"ME12_POLICY_ASSETS");
        var docs=new ArrayList<Document>();
        for(var asset:ASSETS) {
            var content=bytes.get(asset);var object=strictObject(content);
            docs.add(new Document(new Reference(asset,string(object,"revision"),RewardIntent.digest(content)),content));
        }
        var names=JsonParser.parseString(bytes.get(ASSETS.getFirst())).getAsJsonObject();
        return new Me12Policies(names(names.getAsJsonArray("prefixes")),names(names.getAsJsonArray("suffixes")),docs,bundleDigest(docs));
    }
    public List<Reference> references(){return documents.stream().map(Document::reference).toList();}
    public EnemyAffixRegistry registry(){return new EnemyAffixRegistry(JsonParser.parseString(documents.get(3).utf8()).getAsJsonObject());}
    public EnemyBalance balance(){return new EnemyBalance(JsonParser.parseString(documents.get(4).utf8()).getAsJsonObject());}
    public EnemyAffinityRegistry affinities(){return new EnemyAffinityRegistry(JsonParser.parseString(documents.get(5).utf8()).getAsJsonObject());}
    private static void validateContent(String asset,JsonObject object){
        if(asset.equals("name-pools-v2.json")){
            require(object.keySet().equals(Set.of("schemaVersion","revision","format","prefixes","suffixes"))&&integer(object,"schemaVersion")==2
                    &&string(object,"format").equals("{prefix} {suffix}"),"ME12_NAMES_SCHEMA");
            names(object.getAsJsonArray("prefixes"));names(object.getAsJsonArray("suffixes"));
        }else if(asset.equals("master-enemies-v1.json"))new EnemyBalance(object);
        else{
            var canonical=JsonParser.parseString(resource(asset)).getAsJsonObject();var comparable=object.deepCopy();comparable.add("revision",canonical.get("revision"));
            require(comparable.equals(canonical),"ME12_UNAPPROVED_POLICY_CONTENT:"+asset);
        }
    }
    public int threat(String id){return JsonParser.parseString(documents.get(2).utf8()).getAsJsonObject().getAsJsonObject("threatUnits").get(id).getAsInt();}
    private static List<Name> names(JsonArray array){
        Objects.requireNonNull(array,"ME12_NAME_POOL");require(!array.isEmpty()&&array.size()<=256,"ME12_NAME_POOL_SIZE");var result=new ArrayList<Name>();var ids=new HashSet<String>();
        for(var e:array){var n=e.getAsJsonObject();require(n.keySet().equals(Set.of("id","text","tier","nameWeight","signature","fillers")),"ME12_NAME_FIELDS");
            var f=new TreeSet<String>();n.getAsJsonArray("fillers").forEach(v->{require(v.isJsonPrimitive()&&v.getAsJsonPrimitive().isString(),"ME12_FILLER_TYPE");require(f.add(v.getAsString()),"ME12_DUPLICATE_FILLER");});
            var name=new Name(string(n,"id"),string(n,"text"),integer(n,"tier"),integer(n,"nameWeight"),string(n,"signature"),f);
            require(ids.add(name.id())&&!name.signature().equals("ME-023"),"ME12_DUPLICATE_NAME_OR_UNSPECIFIED_SIGNATURE_SELECTOR");result.add(name);
        }return List.copyOf(result);
    }
    public static String bundleDigest(List<Document> documents){return RewardIntent.digest(documents.stream().map(d->d.reference().asset()+"/"+d.reference().revision()+"/"+d.reference().digest()).collect(java.util.stream.Collectors.joining("\n")));}
    private static String string(JsonObject object,String key){var value=Objects.requireNonNull(object.get(key),"ME12_POLICY_FIELD:"+key);require(value.isJsonPrimitive()&&value.getAsJsonPrimitive().isString(),"ME12_POLICY_STRING:"+key);return value.getAsString();}
    private static int integer(JsonObject object,String key){var value=Objects.requireNonNull(object.get(key),"ME12_POLICY_FIELD:"+key);require(value.isJsonPrimitive()&&value.getAsJsonPrimitive().isNumber()&&value.getAsBigDecimal().scale()<=0,"ME12_POLICY_INTEGER:"+key);return value.getAsBigDecimal().intValueExact();}
    private static JsonObject strictObject(String text){
        Objects.requireNonNull(text,"ME12_POLICY_BYTES");require(text.length()<=131072,"ME12_POLICY_SIZE");
        try(var reader=new com.google.gson.stream.JsonReader(new StringReader(text))){
            reader.setStrictness(Strictness.STRICT);validateTokens(reader,0);
            require(reader.peek()==com.google.gson.stream.JsonToken.END_DOCUMENT,"ME12_POLICY_TRAILING_CONTENT");
        }catch(IOException e){throw new IllegalArgumentException("ME12_POLICY_JSON",e);}
        return JsonParser.parseString(text).getAsJsonObject();
    }
    private static void validateTokens(com.google.gson.stream.JsonReader reader,int depth)throws IOException{
        require(depth<=32,"ME12_POLICY_DEPTH");
        switch(reader.peek()){
            case BEGIN_OBJECT->{reader.beginObject();var keys=new HashSet<String>();while(reader.hasNext()){require(keys.add(reader.nextName()),"ME12_POLICY_DUPLICATE_KEY");validateTokens(reader,depth+1);}reader.endObject();}
            case BEGIN_ARRAY->{reader.beginArray();while(reader.hasNext())validateTokens(reader,depth+1);reader.endArray();}
            case STRING,NUMBER->reader.nextString();
            case BOOLEAN->reader.nextBoolean();
            case NULL->reader.nextNull();
            default->throw new IllegalArgumentException("ME12_POLICY_JSON_TOKEN");
        }
    }
    static String resource(String asset){try(var in=Me12Policies.class.getResourceAsStream("/rpg/enemies/"+asset)){if(in==null)throw new IllegalStateException("ME12_MISSING_ASSET:"+asset);return new String(in.readAllBytes(),StandardCharsets.UTF_8).replace("\r\n","\n");}catch(IOException e){throw new UncheckedIOException(e);}}
    private static void affix(String id){require(id!=null&&id.matches("ME-0(0[1-9]|1[0-9]|2[0-7])"),"ME12_AFFIX_ID");}
}
