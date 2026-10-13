package com.inigmasgames.hytalerpg.diagnostics.hro;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;

/** One-shot diagnostic authorization outside Saves; only the HRO worker touches disk. */
final class HroArm {
    static final long TTL_MILLIS=24L*60*60*1000;
    record Action(UUID owner,UUID world,boolean cancel) { }
    record Armed(UUID owner,UUID world) { }
    private HroArm() { }
    static String installation(String path){
        try {var digest=MessageDigest.getInstance("SHA-256");return HexFormat.of().formatHex(digest.digest(
                Path.of(path).toAbsolutePath().normalize().toString().toLowerCase(java.util.Locale.ROOT).getBytes(StandardCharsets.UTF_8)));}
        catch(Exception failure){throw new IllegalArgumentException("INVALID_INSTALLATION_IDENTITY",failure);}
    }
    private static Path path(Path root){return root.resolve("arm-next.json");}
    private static void verifyRoot(Path root)throws IOException {
        Path ancestor=root.toAbsolutePath().normalize();
        while(ancestor!=null&&!Files.exists(ancestor))ancestor=ancestor.getParent();
        if(ancestor==null)throw new IOException("ARM_ROOT_UNAVAILABLE");
        for(Path part:ancestor.toRealPath())if(part.toString().equalsIgnoreCase("Saves"))throw new IOException("ARM_RESOLVES_INTO_SAVES");
        if(Files.exists(root)&&Files.isSymbolicLink(root))throw new IOException("ARM_ROOT_SYMLINK");
    }
    static void write(Path root,String installation,UUID owner,UUID world,long now)throws IOException {
        verifyRoot(root);
        Files.createDirectories(root);
        Path target=path(root),temp=root.resolve("arm-next-"+UUID.randomUUID()+".tmp");
        if(Files.isSymbolicLink(target))throw new IOException("ARM_SYMLINK_REJECTED");
        JsonObject data=new JsonObject();data.addProperty("schemaVersion",1);data.addProperty("owner",owner.toString());
        if(world!=null)data.addProperty("world",world.toString());data.addProperty("installation",installation);
        data.addProperty("createdUtcMillis",now);data.addProperty("expiresUtcMillis",now+TTL_MILLIS);
        Files.writeString(temp,HroBundle.JSON.toJson(data)+"\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
        try {Files.move(temp,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
        finally {Files.deleteIfExists(temp);}
    }
    static void cancel(Path root)throws IOException {verifyRoot(root);Path target=path(root);if(Files.isSymbolicLink(target))throw new IOException("ARM_SYMLINK_REJECTED");Files.deleteIfExists(target);}
    static Armed consume(Path root,String installation,long now)throws IOException {
        verifyRoot(root);
        Path target=path(root);if(!Files.exists(target))return null;
        if(Files.isSymbolicLink(target)||Files.size(target)>4096)throw new IOException("INVALID_ARM_RECORD");
        JsonObject data=HroOffline.object(target);
        if(!HroOffline.value(data,"schemaVersion").equals("1"))throw new IOException("ARM_SCHEMA");
        long created,expires;UUID owner,world;
        try {created=data.get("createdUtcMillis").getAsLong();expires=data.get("expiresUtcMillis").getAsLong();
            owner=UUID.fromString(HroOffline.required(data,"owner"));
            world=data.has("world")?UUID.fromString(data.get("world").getAsString()):null;}
        catch(RuntimeException failure){throw new IOException("INVALID_ARM_RECORD",failure);}
        if(expires-created!=TTL_MILLIS||created>now+60_000)throw new IOException("INVALID_ARM_EXPIRY");
        if(now>=expires){Files.delete(target);return null;}
        if(!installation.equals(HroOffline.value(data,"installation")))return null;
        // Move before starting a capture: a crash or interrupted boot cannot replay the authorization.
        Files.move(target,root.resolve("arm-consumed-"+UUID.randomUUID()+".json"),StandardCopyOption.ATOMIC_MOVE);
        return new Armed(owner,world);
    }
}
