package com.inigmasgames.hytalerpg.diagnostics.hro;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;

/** Shared offline bounds. Inputs are data; no class loading, commands or save access. */
final class HroOffline {
    private HroOffline() { }
    static JsonObject object(Path path) throws IOException {
        if (Files.isSymbolicLink(path) || Files.size(path)>1024*1024) throw new IOException("METADATA_LIMIT_OR_LINK");
        return parse(Files.readString(path));
    }
    static JsonObject parse(String text) throws IOException {
        // Bound nesting before Gson allocates a recursive tree, including escaped quotes.
        int depth=0; boolean quoted=false,escaped=false;
        for(char c:text.toCharArray()) {
            if(quoted) { if(escaped) escaped=false; else if(c=='\\') escaped=true; else if(c=='\"') quoted=false; }
            else if(c=='\"') quoted=true;
            else if(c=='{'||c=='[') { if(++depth>16)throw new IOException("JSON_DEPTH_LIMIT"); }
            else if(c=='}'||c==']') { if(--depth<0)throw new IOException("INVALID_JSON"); }
        }
        try { return JsonParser.parseString(text).getAsJsonObject(); }
        catch(RuntimeException invalid) { throw new IOException("INVALID_JSON",invalid); }
    }
    static String value(JsonObject object,String key) {
        JsonElement value=object.get(key);
        return value!=null&&value.isJsonPrimitive()?value.getAsString():"UNKNOWN";
    }
    static String required(JsonObject object,String key) throws IOException {
        String value=value(object,key);
        if(value.isBlank()||value.equals("UNKNOWN")||value.length()>256)throw new IOException("MISSING_OR_INVALID_"+key);
        return value;
    }
    static String hash(Object object) {
        return hashBytes(canonical(HroBundle.JSON.toJsonTree(object)).getBytes(StandardCharsets.UTF_8));
    }
    static String hashBytes(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch(NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private static String canonical(JsonElement value) {
        if(value.isJsonObject()) {
            var sorted=new TreeMap<String,String>();value.getAsJsonObject().entrySet().forEach(e->sorted.put(e.getKey(),canonical(e.getValue())));
            return HroBundle.JSON.toJson(sorted);
        }
        if(value.isJsonArray()) { var list=new ArrayList<String>();value.getAsJsonArray().forEach(v->list.add(canonical(v)));return HroBundle.JSON.toJson(list); }
        return value.toString();
    }
    static void create(Path path,Object object) throws IOException {
        Path absolute=path.toAbsolutePath().normalize();
        Path parent=absolute.getParent();if(parent==null)throw new IOException("OUTPUT_PARENT_REQUIRED");
        Path real=parent.toRealPath();
        for(Path part:real)if(part.toString().equalsIgnoreCase("Saves"))throw new IOException("OUTPUT_INSIDE_SAVES_REJECTED");
        String json=HroBundle.JSON.toJson(object);
        if(json.length()>1024*1024)throw new IOException("OUTPUT_LIMIT");
        Files.writeString(absolute,json+"\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
    }
    static void derived(Path path,Object value)throws IOException {
        Path absolute=path.toAbsolutePath().normalize();
        if(Files.isSymbolicLink(absolute))throw new IOException("OUTPUT_LINK_REJECTED");
        Path parent=Objects.requireNonNull(absolute.getParent());
        for(Path part:parent.toRealPath())if(part.toString().equalsIgnoreCase("Saves"))throw new IOException("OUTPUT_INSIDE_SAVES_REJECTED");
        String json=HroBundle.JSON.toJson(value);if(json.length()>4*1024*1024)throw new IOException("DERIVED_REPORT_LIMIT");
        Files.writeString(absolute,json+"\n",StandardCharsets.UTF_8);
    }
}
