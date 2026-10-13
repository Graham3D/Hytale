package com.inigmasgames.hytalerpg.diagnostics.hro;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.zip.GZIPOutputStream;

/** Single diagnostic worker owns all filesystem, JSON, gzip, indexing and analysis work. */
final class HroBundle implements AutoCloseable {
    static final long MAX_EVENTS_BYTES=256L*1024*1024, SEGMENT_BYTES=4L*1024*1024;
    static final Gson JSON=new GsonBuilder().serializeNulls().disableHtmlEscaping().create();
    private final Path directory;
    private final Map<String,Object> manifest;
    private final HroContracts contracts;
    private final Set<String> providers;
    private final HroMechanicalReport mechanical=new HroMechanicalReport();
    private BufferedWriter events,index;
    private long total,segmentBytes,line,indexBytes,eventCount;
    private final List<Double> nativeWorldMillis=new ArrayList<>(1024),rpgWallMillis=new ArrayList<>(1024);
    private final List<Map<String,Object>> checkpoints=new ArrayList<>(32);
    private long markerCount;
    private long nativeSamples,unpairedNativeSamples;
    private int segment;
    private boolean finished;
    HroBundle(Path directory,Map<String,Object> identity,Set<String> providers)throws IOException {
        this.directory=directory; this.manifest=new LinkedHashMap<>(identity); this.providers=providers;contracts=new HroContracts(providers);
        Files.createDirectories(directory.resolve("events")); Files.createDirectories(directory.resolve("indexes"));
        writeJson("manifest.json",manifest);
        writeJson("coverage.json",contracts.coverage(true));
        writeJson("findings.json",Map.of("findings",List.of(),"reason","CAPTURE_IN_PROGRESS"));
        writeJson("performance.json",Map.of("state","RECORDING","qualification","INCOMPLETE"));
        writeJson("integrity.json",Map.of("evidenceComplete",false,"termination","RECORDING","tailLoss","UNKNOWN_UNTIL_FINALIZED"));
        Files.writeString(directory.resolve("summary.md"),"# HRO capture in progress\n\nNo final gameplay verdict; unflushed tail unknown.\n");
        writeJson("capabilities.json",Map.of("registeredContracts",providers,"clientVersion","UNOBSERVABLE",
                "physicalInput","UNOBSERVABLE","renderAcknowledgement","UNOBSERVABLE",
                "nativeTick","NULL_WHEN_NOT_AT_OWNER_TICK","slots",Map.of("skill01","Ability2","skill02","Ability3",
                        "skill03","UNBOUND","skill04","UNBOUND"),"legacyRecorders","INDEPENDENT_NOT_ENABLED_BY_HRO"));
        index=Files.newBufferedWriter(directory.resolve("indexes/operations.jsonl"),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
        rotate();
    }
    private String segmentName(){return String.format(Locale.ROOT,"events/segment-%04d.jsonl",segment);}
    void event(HroEvent event)throws IOException {
        event=HroEvidence.normalize(event);
        JsonObject envelope=JSON.toJsonTree(event).getAsJsonObject();
        envelope.addProperty("eventId",event.runId()+":"+event.sequence());
        envelope.addProperty("producerId","HroRecorder.admission");envelope.addProperty("producerSequence",event.sequence());
        envelope.add("processId",JSON.toJsonTree(manifest.get("processId")));envelope.add("bootId",JSON.toJsonTree(manifest.get("bootId")));
        String json=JSON.toJson(envelope); byte[] encoded=json.getBytes(StandardCharsets.UTF_8);
        if(total+encoded.length+1>MAX_EVENTS_BYTES)throw new IOException("EVENT_BYTE_BUDGET");
        if(segmentBytes+encoded.length+1>SEGMENT_BYTES)rotate();
        events.write(json);events.write('\n');line++;eventCount++;total+=encoded.length+1;segmentBytes+=encoded.length+1;
        String locator=segmentName()+".gz:"+line;
        String indexLine=JSON.toJson(Map.of("operationId",event.operationId(),"eventId",event.sequence(),"location",locator));
        indexBytes+=indexLine.getBytes(StandardCharsets.UTF_8).length+1;
        if(indexBytes>96L*1024*1024)throw new IOException("INDEX_BYTE_BUDGET");
        index.write(indexLine);
        index.write('\n');
        if(providers.contains(event.contract()))contracts.registered(event.contract());
        contracts.accept(event,locator);
        mechanical.accept(event,locator);
        if(event.boundary().equals("MARK"))markerCount++;
        if(event.boundary().equals("MARK")&&checkpoints.size()<32){
            var marker=new LinkedHashMap<String,Object>();marker.put("label",event.facts().getOrDefault("label","USER_CHECKPOINT"));
            marker.put("runId",event.runId());marker.put("connectionId",event.connectionId());
            marker.put("worldSegmentId",event.worldSegmentId());marker.put("utcMillis",event.utcMillis());
            marker.put("operationId",event.operationId());marker.put("locator",locator);checkpoints.add(marker);
        }
        if(event.contract().equals("PERFORMANCE")&&event.boundary().equals("WORLD_TICK")){
            if("true".equals(event.facts().get("wholeWorldTickPaired"))){
                nativeSamples++;
                String whole=event.facts().get("wholeWorldTickMs"),rpg=event.facts().get("rpgWallMs");
                if(nativeWorldMillis.size()<1024&&whole!=null&&rpg!=null)try{
                    double nativeValue=Double.parseDouble(whole),rpgValue=Double.parseDouble(rpg);
                    if(Double.isFinite(nativeValue)&&Double.isFinite(rpgValue)&&nativeValue>=0&&rpgValue>=0){
                        nativeWorldMillis.add(nativeValue);rpgWallMillis.add(rpgValue);
                    }else unpairedNativeSamples++;
                }catch(NumberFormatException badSample){unpairedNativeSamples++;}
                else if(whole==null||rpg==null)unpairedNativeSamples++;
            }else unpairedNativeSamples++;
        }
    }
    void flush()throws IOException {events.flush();index.flush();}
    long eventBytes(){return total;}
    Map<String,Object> nativeTiming(){
        var result=new LinkedHashMap<String,Object>();result.put("source","NativeRpgTickMetrics owner-thread prior-tick pairing");
        result.put("pairedSamples",nativeSamples);result.put("unpairedSamples",unpairedNativeSamples);
        result.put("timingSamplesAnalyzed",nativeWorldMillis.size());result.put("samplePolicy","FIRST_1024_PAIRED_SAMPLES; not a full-session distribution when capped");
        result.put("wholeWorldTickP95Ms",percentile(nativeWorldMillis,.95));result.put("wholeWorldTickP99Ms",percentile(nativeWorldMillis,.99));
        result.put("rpgWallP95Ms",percentile(rpgWallMillis,.95));result.put("rpgWallP99Ms",percentile(rpgWallMillis,.99));
        result.put("traceOffOnComparison","UNVERIFIED_NO_MATCHED_RUNS");return result;
    }
    private static Object percentile(List<Double> values,double fraction){if(values.isEmpty())return "UNVERIFIED";
        var ordered=new ArrayList<>(values);ordered.sort(Double::compare);return ordered.get(Math.min(ordered.size()-1,(int)Math.ceil(ordered.size()*fraction)-1));}
    private void rotate()throws IOException {
        if(events!=null){events.close();compress(directory.resolve(segmentName()+".pending"),directory.resolve(segmentName()+".gz"));}
        segment++;segmentBytes=0;line=0;
        events=Files.newBufferedWriter(directory.resolve(segmentName()+".pending"),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
    }
    static void compress(Path raw,Path target)throws IOException {
        Path temp=target.resolveSibling(target.getFileName()+".tmp");
        if(Files.isSymbolicLink(raw)||Files.isSymbolicLink(temp)||Files.isSymbolicLink(target))throw new IOException("SYMLINK_EVIDENCE_REJECTED");
        try(var in=Files.newInputStream(raw);var out=new GZIPOutputStream(Files.newOutputStream(temp),32*1024)) {in.transferTo(out);}
        Files.move(temp,target,StandardCopyOption.REPLACE_EXISTING);
        Files.delete(raw);
    }
    void finish(long drops,String reason,Map<String,Object> performance)throws IOException {
        if(finished)return;finished=true;
        events.close();index.close();compress(directory.resolve(segmentName()+".pending"),directory.resolve(segmentName()+".gz"));
        providers.forEach(contracts::registered);
        writeJson("capabilities.json",Map.of("registeredContracts",Set.copyOf(providers),"clientVersion","UNOBSERVABLE",
                "physicalInput","UNOBSERVABLE","renderAcknowledgement","UNOBSERVABLE",
                "nativeTick","NULL_WHEN_NOT_AT_OWNER_TICK","slots",Map.of("skill01","Ability2","skill02","Ability3",
                        "skill03","UNBOUND","skill04","UNBOUND"),"legacyRecorders","INDEPENDENT_NOT_ENABLED_BY_HRO"));
        contracts.lost(drops);
        boolean incomplete=!reason.equals("STOPPED") && !reason.equals("EXPIRED");
        var coverage=contracts.coverage(incomplete);
        var findings=contracts.findings(incomplete);
        var cascades=HroMaps.cascades(findings);
        writeJson("coverage.json",coverage);
        writeJson("mechanical.json",mechanical.snapshot(incomplete||drops>0));
        writeJson("findings.json",Map.of("findings",findings,"omitted",contracts.omittedFindings(),
                "comparison","NO_BASELINE","scope","OBSERVED_OPERATIONS_ONLY"));
        writeJson("performance.json",performance);
        writeJson("implementation-map.json",Map.of("schemaVersion",1,"components",HroMaps.implementation()));
        writeJson("compatibility-diff.json",Map.of("comparison","AUTO_ANALYSIS_SIDECAR","reason","See auto-analysis.json for approved-baseline comparisons when finalization completes"));
        writeJson("cascades.json",cascades);
        writeJson("report.json",Map.of("schemaVersion",1,"findings",HroMaps.details(findings),"checkpoints",checkpoints));
        writeJson("checkpoints.json",Map.of("checkpoints",checkpoints,"omitted",Math.max(0,markerCount-checkpoints.size())));
        writeJson("integrity.json",Map.of("dropped",drops,"termination",reason,"evidenceComplete",drops==0&&!incomplete,
                "uncompressedEventBytes",total,"pendingOperationsAreInconclusive",true,
                "observedDisconnects",performance.getOrDefault("observedDisconnects",0),
                "correlatedReconnects",performance.getOrDefault("correlatedReconnects",0)));
        StringBuilder summary=new StringBuilder("# Hywind Regression Observatory\n\nRun: "+manifest.get("runId")+"\n\nTermination: "+reason+
                "; dropped observations: "+drops+". Scope: invoking subject only.\n\n");
        coverage.forEach((id,value)->summary.append("- ").append(id).append(": ").append(((Map<?,?>)value).get("outcome")).append('\n'));
        summary.append("\nSupported failures: ").append(findings.stream().filter(f->f.outcome()==HroContracts.Outcome.FAIL).count())
                .append("; downstream blockers: ").append(findings.stream().filter(f->f.outcome()==HroContracts.Outcome.BLOCKED).count())
                .append("; evidence gaps: ").append(findings.stream().filter(f->f.outcome()==HroContracts.Outcome.INCONCLUSIVE).count())
                .append("; correlation groups: ").append(cascades.size()).append(".\n");
        findings.stream().filter(f->f.outcome()==HroContracts.Outcome.FAIL||f.outcome()==HroContracts.Outcome.BLOCKED)
                .limit(5).forEach(f->summary.append("- ").append(f.outcome()).append(' ').append(f.contract()).append(" / ")
                        .append(safeSummary(f.operationId())).append(": ").append(safeSummary(f.firstDivergence())).append('\n'));
        long uncovered=coverage.values().stream().filter(value->"NOT_EXERCISED".equals(String.valueOf(((Map<?,?>)value).get("outcome")))).count();
        summary.append("Unexercised contract families: ").append(uncovered).append("; see coverage.json. Unattempted scenarios are not assumed healthy.\n");
        if(!checkpoints.isEmpty())summary.append("\nCheckpoints: ").append(checkpoints.size()).append("; see checkpoints.json and report.json.\n");
        summary.append("\nStart with findings.json, then indexes/operations.jsonl for segment and line locators. "
                +"Read one gzip segment at a time. Automatic approved-baseline comparison status is in auto-analysis.json. Server publication does not prove client rendering. "
                +"Pre-start boot/admission events are not reconstructed. Missing expected custody mode and native outcome boundaries remain gaps.\n");
        Files.writeString(directory.resolve("summary.md"),summary,StandardCharsets.UTF_8);
        manifest.put("state","COMPLETE");manifest.put("termination",reason);manifest.put("finishedUtcMillis",System.currentTimeMillis());
        manifest.put("eventCount",eventCount);manifest.put("segmentCount",segment);
        writeJson("manifest.json",manifest); checksums(directory);
    }
    private static String safeSummary(String value){return value==null?"UNKNOWN":value.replaceAll("[\\r\\n]"," ").substring(0,Math.min(120,value.length()));}
    private void writeJson(String name,Object data)throws IOException {
        Files.writeString(directory.resolve(name),JSON.toJson(data)+"\n",StandardCharsets.UTF_8);
    }
    static String sha256(Path path)throws IOException {
        try {
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            try(var input=new DigestInputStream(Files.newInputStream(path),digest)){input.transferTo(OutputStream.nullOutputStream());}
            return HexFormat.of().formatHex(digest.digest());
        }catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
    static void checksums(Path directory)throws IOException {
        try(var files=Files.walk(directory);var out=Files.newBufferedWriter(directory.resolve("checksums.sha256"),StandardCharsets.UTF_8)) {
            for(Path path:files.filter(Files::isRegularFile).filter(p->!p.getFileName().toString().equals("checksums.sha256")).sorted().toList()) {
                out.write(sha256(path)+"  "+directory.relativize(path).toString().replace('\\','/')+"\n");
            }
        }
    }
    /** Explicit offline recovery; never consults current BuildIdentity. Original manifest is retained. */
    static void recover(Path directory)throws IOException {
        Path base=directory.toRealPath();
        for(Path part:base)if(part.toString().equalsIgnoreCase("Saves"))throw new IOException("RECOVERY_INSIDE_SAVES_REJECTED");
        Path manifestPath=directory.resolve("manifest.json");
        if(!Files.isRegularFile(manifestPath))throw new IOException("ORIGIN_MANIFEST_MISSING");
        if(Files.isSymbolicLink(manifestPath)||Files.size(manifestPath)>1024*1024)throw new IOException("INVALID_ORIGIN_MANIFEST");
        JsonObject manifest;
        try(var reader=Files.newBufferedReader(manifestPath)){manifest=JsonParser.parseReader(reader).getAsJsonObject();}
        JsonElement state=manifest.get("state");
        if(state!=null&&"COMPLETE".equals(state.getAsString()))return;
        Path eventRoot=directory.resolve("events");
        if(Files.isSymbolicLink(eventRoot)||!eventRoot.toRealPath().startsWith(base))throw new IOException("EVENT_PATH_ESCAPE");
        try(var files=Files.list(eventRoot)) {
            var pending=files.filter(p->p.getFileName().toString().matches("segment-[0-9]{4}\\.jsonl\\.pending")).limit(66).toList();
            if(pending.size()>65)throw new IOException("RECOVERY_SEGMENT_COUNT_LIMIT");
            for(Path raw:pending) {
                if(Files.isSymbolicLink(raw)||!raw.toRealPath().startsWith(base))throw new IOException("EVENT_PATH_ESCAPE");
                // In-place truncate only an uncommitted partial final line; never rewrite complete evidence.
                try(var file=new RandomAccessFile(raw.toFile(),"rw")) {
                    long length=file.length(); if(length>SEGMENT_BYTES)throw new IOException("RECOVERY_SEGMENT_LIMIT");
                    while(length>0){file.seek(length-1);if(file.read()=='\n')break;length--;}
                    file.setLength(length);
                }
                compress(raw,raw.resolveSibling(Objects.requireNonNull(raw.getFileName()).toString().replace(".pending",".gz")));
            }
        }
        manifest.addProperty("state","RECOVERED_PARTIAL");manifest.addProperty("recoveryUtcMillis",System.currentTimeMillis());
        manifest.addProperty("recoveryBuild","UNKNOWN_OFFLINE_TOOL");
        Files.writeString(manifestPath,JSON.toJson(manifest)+"\n");
        Files.writeString(directory.resolve("integrity.json"),JSON.toJson(Map.of("evidenceComplete",false,"termination","INTERRUPTED","tailLoss","UNKNOWN")));
        Files.writeString(directory.resolve("coverage.json"),JSON.toJson(new HroContracts(Set.of()).coverage(true)));
        Files.writeString(directory.resolve("findings.json"),"{\"findings\":[],\"reason\":\"INTERRUPTED_CAPTURE_REQUIRES_REANALYSIS\"}");
        Files.writeString(directory.resolve("summary.md"),"# Recovered partial HRO capture\n\nOriginal captureBuild preserved. All contracts INCONCLUSIVE; unflushed tail unknown.\n");
        checksums(directory);
    }
    @Override public void close()throws IOException {
        if(events!=null)events.close();if(index!=null)index.close();
    }
}
