package com.inigmasgames.hytalerpg.diagnostics.hro;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.GZIPInputStream;

/** Explicit v1 parsers for existing JSONL producers; never rewrites or executes source archives. */
final class HroLegacy {
    private HroLegacy() { }
    static final Set<String> FORMATS=Set.of("skill-v1","ui-v1","combat-v1","gear-v1","spawn-v1");
    private static final Set<String> FIELDS=Set.of("slot","skillId","expectedSpatial","activeSlot","inactiveSlot","translated","quantityBefore","quantityAfter","singleCustodian",
            "attempted","accepted","postAbsorption","healthBefore","healthAfter","healthLost","reconciliation","cancelled","blocked","equipmentRevision","affixId","rarity","provenance","environment","count");
    static void importFile(Path source,String format,Path output,Path manifestPath)throws IOException {
        if(!FORMATS.contains(format))throw new IOException("UNSUPPORTED_LEGACY_FORMAT");
        if(Files.isSymbolicLink(source)||Files.size(source)>64L*1024*1024)throw new IOException("LEGACY_INPUT_LIMIT_OR_LINK");
        JsonObject manifest=manifestPath==null?new JsonObject():HroOffline.object(manifestPath);
        if(manifestPath!=null&&!HroOffline.value(manifest,"schemaVersion").equals("1"))throw new IOException("LEGACY_MANIFEST_SCHEMA");
        String sourceHash=HroBundle.sha256(source);
        String sourceName=Objects.requireNonNull(source.getFileName(),"source filename").toString();
        boolean gzip=sourceName.endsWith(".gz");
        if(manifestPath!=null) {
            String expected=HroOffline.value(manifest,gzip?"compressedSha256":"uncompressedSha256");
            if(!expected.equals("UNKNOWN")&&!expected.equalsIgnoreCase(sourceHash))throw new IOException("LEGACY_CHECKSUM_MISMATCH");
        }
        var rows=new ArrayList<Map<String,Object>>();var builds=new TreeSet<String>();long bytes=0,offset=0,lineNumber=0,omitted=0;int retainedCharacters=0;boolean partial=false;
        try(var file=Files.newInputStream(source);var input=new BufferedInputStream(gzip?new GZIPInputStream(file):file)) {
            var line=new ByteArrayOutputStream();
            for(int value;(value=input.read())!=-1;) {
                if(++bytes>64L*1024*1024)throw new IOException("LEGACY_DECOMPRESSION_LIMIT");
                if(value=='\n') {
                    if(line.size()>0) {
                        JsonObject raw=HroOffline.parse(line.toString(StandardCharsets.UTF_8));lineNumber++;
                        Map<String,Object> row=normalize(raw,format,lineNumber,offset);
                        if(builds.size()<33)builds.add(HroOffline.value(raw,"buildVersion")+"/"+HroOffline.value(raw,"hytaleBuild"));
                        int characters=HroBundle.JSON.toJson(row).length();
                        if(rows.size()<256&&retainedCharacters+characters<=768*1024){rows.add(row);retainedCharacters+=characters;}else omitted++;
                    }
                    line.reset();offset=bytes;
                } else { if(line.size()>=64*1024)throw new IOException("LEGACY_LINE_LIMIT");line.write(value); }
            }
            partial=line.size()>0;
        }
        var report=new LinkedHashMap<String,Object>();report.put("schemaVersion",1);report.put("parser",format);report.put("sourceSha256",sourceHash);
        report.put("sourceName",sourceName);report.put("offsetUnits","DECOMPRESSED_UTF8_BYTES");
        var declaredBuild=Map.of("rpgRevision",HroOffline.value(manifest,"rpgRevision"),"buildVersion",HroOffline.value(manifest,"buildVersion"),"hytaleBuild",HroOffline.value(manifest,"hytaleBuild"));
        boolean recovered=HroOffline.value(manifest,"archiveState").contains("RECOVER");
        // Old recovery wrote VERIFIED manifests using the recovery process BuildIdentity.
        // Even VERIFIED therefore cannot establish the original capture binary.
        report.put("captureBuild","UNKNOWN_LEGACY_MANIFEST_MAY_DESCRIBE_RECOVERY");report.put("archiveBuildClaim",declaredBuild);
        report.put("recoveryBuild",recovered?declaredBuild:"UNKNOWN_LEGACY_MANIFEST_DOES_NOT_DISTINGUISH_RECOVERY");report.put("observedBuilds",builds.stream().limit(32).toList());
        report.put("originTrust","UNVERIFIED; archive metadata is a claim, checksum is byte integrity only");
        report.put("events",rows);report.put("omittedEvents",omitted);report.put("partialTail",partial);report.put("mixedBuilds",builds.size()>1);
        report.put("outcome","INCONCLUSIVE");report.put("reason","LEGACY_CORRELATION_AND_NATIVE_PROOF_NOT_UPGRADED; raw originals retained at source");
        HroOffline.create(output,report);
    }
    static Map<String,Object> normalize(JsonObject raw,String format,long line,long offset)throws IOException {
        String name=switch(format) {
            case "skill-v1","ui-v1" -> HroOffline.required(raw,"eventType");
            case "gear-v1" -> HroOffline.required(raw,"event");
            case "spawn-v1" -> raw.has("stage")?HroOffline.required(raw,"stage"):HroOffline.required(raw,"type");
            case "combat-v1" -> raw.has("hitId")?"NATIVE_DAMAGE_RESULT":HroOffline.required(raw,"eventType");
            default -> throw new IOException("UNSUPPORTED_LEGACY_FORMAT");
        };
        JsonObject details=raw.has("details")&&raw.get("details").isJsonObject()?raw.getAsJsonObject("details"):raw;
        var facts=new TreeMap<String,String>();
        for(String key:FIELDS) { String value=HroOffline.value(details,key);if(!value.equals("UNKNOWN")&&value.length()<=192)facts.put(key,value); }
        String correlation=HroOffline.value(raw,format.equals("combat-v1")?"hitId":"correlationId");
        var result=new LinkedHashMap<String,Object>();result.put("rawEventName",name);result.put("sourceLine",line);result.put("sourceOffset",offset);
        result.put("facts",facts);result.put("correlation",correlation.equals("UNKNOWN")||correlation.isBlank()?"MISSING":"legacy:"+HroOffline.hash(correlation));
        result.put("timestamp",HroOffline.value(raw,format.equals("spawn-v1")?"time":"timestamp"));
        result.put("buildVersion",HroOffline.value(raw,"buildVersion"));result.put("hytaleBuild",HroOffline.value(raw,"hytaleBuild"));
        result.put("missing",List.of("HRO_RUN","CONNECTION_GENERATION","APPROVED_SCENARIO","CLIENT_ACK"));return result;
    }
}
