package com.inigmasgames.hytalerpg.diagnostics.hro;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.GZIPInputStream;

/** Offline bounded reader. Does not load Hytale, native saves, or any recorded class by name. */
public final class HroTool {
    private HroTool() { }
    public static void main(String[] args)throws IOException {
        if(args.length<2)throw new IllegalArgumentException("analyze|recover|extract|recovery-join <bundle> [operationId|later-bundle]");
        Path directory=Path.of(args[1]).toAbsolutePath().normalize();
        switch(args[0]) {
            case "recover" -> HroBundle.recover(directory);
            case "analyze" -> analyze(directory);
            case "recovery-join" -> {
                if(args.length!=3)throw new IllegalArgumentException("recovery-join <earlier-bundle> <later-bundle>");
                recoveryJoin(directory,Path.of(args[2]).toAbsolutePath().normalize());
            }
            case "extract" -> {
                if(args.length!=3)throw new IllegalArgumentException("extract requires operationId");
                verifyChecksums(directory);int[] emitted={0};
                read(directory,(event,locator)->{if(event.operationId().equals(args[2])&&emitted[0]++<200)System.out.println(locator+" "+HroBundle.JSON.toJson(event));});
            }
            default -> throw new IllegalArgumentException("Unknown HRO operation");
        }
    }
    static void analyze(Path directory)throws IOException {
        verifyChecksums(directory);
        JsonObject manifest=json(directory.resolve("manifest.json"));
        JsonObject capabilities=json(directory.resolve("capabilities.json"));
        Set<String> registered=new HashSet<>();
        capabilities.getAsJsonArray("registeredContracts").forEach(value->registered.add(value.getAsString()));
        HroContracts contracts=new HroContracts(registered);
        HroMechanicalReport mechanical=new HroMechanicalReport();
        JsonObject integrity=json(directory.resolve("integrity.json"));
        boolean complete="COMPLETE".equals(manifest.get("state").getAsString())
                && integrity.has("evidenceComplete") && integrity.get("evidenceComplete").getAsBoolean();
        long dropped=integrity.has("dropped")?integrity.get("dropped").getAsLong():1;
        contracts.lost(dropped);
        long[] count={0};String run=manifest.get("runId").getAsString();
        read(directory,(event,locator)->{
            if(!run.equals(event.runId()) || event.schemaVersion()!=1)throw new IllegalArgumentException("EVENT_ORIGIN_OR_SCHEMA_MISMATCH");
            if(event.sequence()!=++count[0])contracts.lost(1);
            contracts.accept(event,locator);
            mechanical.accept(event,locator);
        });
        if(manifest.has("eventCount")&&count[0]!=manifest.get("eventCount").getAsLong())contracts.lost(1);
        Files.writeString(directory.resolve("analysis.json"),HroBundle.JSON.toJson(Map.of("captureBuild",manifest.get("captureBuild"),
                "coverage",contracts.coverage(!complete),"findings",contracts.findings(!complete),"mechanical",mechanical.snapshot(!complete||dropped>0))));
    }
    static void verifyChecksums(Path directory)throws IOException {
        Path base=directory.toAbsolutePath().normalize();Path checksum=base.resolve("checksums.sha256");
        if(Files.size(checksum)>64*1024)throw new IOException("CHECKSUM_INDEX_LIMIT");
        Set<Path> verified = new HashSet<>();
        try(var lines=Files.lines(checksum,StandardCharsets.UTF_8)) {
            List<String> entries = lines.limit(257).toList();
            if (entries.size() > 256) throw new IOException("CHECKSUM_INDEX_LIMIT");
            for(String line:entries) {
                String[] pair=line.split("  ",2);if(pair.length!=2)throw new IOException("INVALID_CHECKSUM");
                Path file=base.resolve(pair[1]).normalize();
                if(!file.startsWith(base)||Files.isSymbolicLink(file)||!file.toRealPath().startsWith(base.toRealPath()))throw new IOException("CHECKSUM_PATH_ESCAPE");
                if(!pair[0].equals(HroBundle.sha256(file)))throw new IOException("CHECKSUM_MISMATCH");
                if (!verified.add(file)) throw new IOException("DUPLICATE_CHECKSUM_ENTRY");
            }
        }
        for (String required : List.of("manifest.json", "capabilities.json", "integrity.json", "coverage.json",
                "findings.json", "summary.md", "performance.json", "indexes/operations.jsonl")) {
            if (!verified.contains(base.resolve(required))) throw new IOException("UNCHECKED_METADATA");
        }
        try (var events = Files.list(base.resolve("events"))) {
            if (events.filter(path -> path.getFileName().toString().endsWith(".gz"))
                    .anyMatch(path -> !verified.contains(path))) throw new IOException("UNCHECKED_EVENT_SEGMENT");
        }
    }
    /** Explicit receipt join, not a gameplay baseline comparison or an implicit legacy-capture import. */
    static void recoveryJoin(Path earlier,Path later)throws IOException {
        verifyChecksums(earlier);verifyChecksums(later);
        var first=json(earlier.resolve("manifest.json"));var second=json(later.resolve("manifest.json"));
        boolean complete="COMPLETE".equals(first.get("state").getAsString())&&"COMPLETE".equals(second.get("state").getAsString())
                &&json(earlier.resolve("integrity.json")).get("evidenceComplete").getAsBoolean()
                &&json(later.resolve("integrity.json")).get("evidenceComplete").getAsBoolean();
        boolean sameBuild=Objects.equals(first.get("captureBuild"),second.get("captureBuild"));
        Map<String,HroEvent> saved=new HashMap<>();Map<String,String> locators=new HashMap<>();
        long[] omitted={0},firstCount={0},secondCount={0};boolean[] valid={true};
        read(earlier,(event,locator)->{
            if(!validRecoveryEvent(event,first,++firstCount[0]))valid[0]=false;
            if(event.contract().equals("PERSISTENCE.RECOVERY")&&event.boundary().equals("SAVED")){
                String subject=event.facts().get("durableSubject");if(subject==null)return;
                if(saved.size()>=2048&&!saved.containsKey(subject)){omitted[0]++;return;}
                saved.put(subject,event);locators.put(subject,locator);
            }
        });
        List<Map<String,Object>> results=new ArrayList<>();
        read(later,(event,locator)->{
            if(!validRecoveryEvent(event,second,++secondCount[0]))valid[0]=false;
            if(!event.contract().equals("PERSISTENCE.RECOVERY")||!event.boundary().equals("LOADED"))return;
            if(results.size()>=128){omitted[0]++;return;}
            String subject=event.facts().get("durableSubject");HroEvent prior=saved.get(subject);
            var state=new HashMap<String,String>();if(prior!=null)state.putAll(prior.facts());
            var verdict=HroMechanicalContracts.accept(event,state);
            boolean ordered=prior!=null&&prior.utcMillis()<=event.utcMillis()&&!prior.runId().equals(event.runId());
            results.add(Map.of("outcome",complete&&sameBuild&&ordered&&omitted[0]==0?verdict.outcome():HroContracts.Outcome.INCONCLUSIVE,
                    "reason",!complete?"CAPTURE_INCOMPLETE":!sameBuild?"BUILD_IDENTITY_DIFFERS":!ordered?"EARLIER_DURABLE_EVIDENCE_MISSING_OR_UNORDERED":verdict.reason(),
                    "earlierEvidence",locators.getOrDefault(subject,"MISSING"),"laterEvidence",locator,"durableSubject",subject==null?"UNKNOWN":subject));
        });
        if(omitted[0]>0||!valid[0]||!first.has("eventCount")||!second.has("eventCount")
                ||first.get("eventCount").getAsLong()!=firstCount[0]||second.get("eventCount").getAsLong()!=secondCount[0])
            results.replaceAll(row->{var gap=new HashMap<>(row);gap.put("outcome",HroContracts.Outcome.INCONCLUSIVE);gap.put("reason","RECOVERY_EVIDENCE_INCOMPLETE_OR_INVALID");return gap;});
        Files.writeString(later.resolve("recovery-analysis.json"),HroBundle.JSON.toJson(Map.of("earlierRun",first.get("runId"),
                "laterRun",second.get("runId"),"comparison","DURABLE_RECEIPT_JOIN_ONLY","findings",results,"omitted",omitted[0])));
    }
    private static boolean validRecoveryEvent(HroEvent e,JsonObject manifest,long sequence){return e.schemaVersion()==1
            &&e.sequence()==sequence&&manifest.get("runId").getAsString().equals(e.runId());}
    private static JsonObject json(Path path)throws IOException {
        if(Files.size(path)>1024*1024)throw new IOException("METADATA_LIMIT");
        try(var reader=Files.newBufferedReader(path)){return JsonParser.parseReader(reader).getAsJsonObject();}
    }
    @FunctionalInterface interface Visitor {void accept(HroEvent event,String locator);}
    static void read(Path directory,Visitor visitor)throws IOException {
        long total=0;
        try(var paths=Files.list(directory.resolve("events"))) {
            List<Path> segments=paths.filter(p->p.getFileName().toString().matches("segment-[0-9]{4}\\.jsonl\\.gz")).sorted().limit(66).toList();
            if(segments.size()>65)throw new IOException("SEGMENT_COUNT_LIMIT");
            for(Path path:segments) {
                if(Files.isSymbolicLink(path)||!path.toRealPath().startsWith(directory.toRealPath()))throw new IOException("EVENT_PATH_ESCAPE");
                long bytes=0,lineNumber=0;
                try(var input=new BufferedInputStream(new GZIPInputStream(Files.newInputStream(path),32*1024))) {
                    var line=new ByteArrayOutputStream(4096);
                    for(int value;(value=input.read())!=-1;) {
                        if(++bytes>HroBundle.SEGMENT_BYTES || ++total>HroBundle.MAX_EVENTS_BYTES)throw new IOException("DECOMPRESSED_BYTE_LIMIT");
                        if(value=='\n') {
                            if(line.size()>0)visitor.accept(HroBundle.JSON.fromJson(line.toString(StandardCharsets.UTF_8),HroEvent.class),
                                    "events/"+path.getFileName()+":"+(++lineNumber));
                            line.reset();
                        }else {
                            if(line.size()>=HroRecorder.RESERVATION)throw new IOException("EVENT_LINE_LIMIT");
                            line.write(value);
                        }
                    }
                    if(line.size()>0)throw new IOException("PARTIAL_LINE");
                }
            }
        }
    }
}
