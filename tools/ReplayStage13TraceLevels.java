import com.google.gson.*;
import com.inigmasgames.hytalerpg.diagnostics.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/** Offline replay through the production router, not a connected or timing-qualification claim. */
class ReplayStage13TraceLevels {
    public static void main(String[] args)throws Exception{
        var gson=new GsonBuilder().disableHtmlEscaping().create();var input=new ArrayList<RpgTraceRecord>();
        try(var lines=Files.lines(Path.of(args[0]))){lines.forEach(line->{var json=JsonParser.parseString(line).getAsJsonObject();
            if(Instant.parse(json.get("timestamp").getAsString()).compareTo(Instant.parse(args[1]))>=0)input.add(gson.fromJson(json,RpgTraceRecord.class));});}
        var results=new LinkedHashMap<String,Object>();
        for(var level:SkillTraceLevel.values()){
            var output=new ArrayList<RpgTraceRecord>();var clock=new AtomicLong();var router=new SkillTraceRouter(level,output::add,clock::get);
            for(var record:input){clock.set(Instant.parse(record.timestamp()).toEpochMilli()*1_000_000);router.trace(record);}router.flush();
            long ticks=input.stream().filter(r->r.eventType()==RpgTraceEventType.NATIVE_RPG_TICK_SAMPLE).count();
            long represented=output.stream().filter(r->r.eventType()==RpgTraceEventType.NATIVE_RPG_TICK_SUMMARY).mapToLong(r->((Number)r.details().get("sampleCount")).longValue()).sum();
            long raw=output.stream().filter(r->r.eventType()==RpgTraceEventType.NATIVE_RPG_TICK_SAMPLE).count();
            if(represented+raw!=ticks)throw new AssertionError("Lost samples");
            for(var r:input)if(r.eventType()!=RpgTraceEventType.NATIVE_RPG_TICK_SAMPLE&&r.eventType()!=RpgTraceEventType.COMPILE_STAGE&&!output.contains(r))throw new AssertionError("Lost event "+r.eventType());
            if(level==SkillTraceLevel.PERFORMANCE&&!input.stream().filter(r->r.eventType()==RpgTraceEventType.NATIVE_RPG_TICK_SAMPLE).toList().equals(output.stream().filter(r->r.eventType()==RpgTraceEventType.NATIVE_RPG_TICK_SAMPLE).toList()))throw new AssertionError("Modified raw samples");
            long bytes=output.stream().mapToLong(r->(gson.toJson(r)+System.lineSeparator()).getBytes(StandardCharsets.UTF_8).length).sum();
            results.put(level.name(),Map.of("inputRecords",input.size(),"outputRecords",output.size(),"rawTicks",raw,"aggregatedTicks",represented,"serializedOutputBytes",bytes,"eventDrivenRecordsPreserved",true));
        }
        var report=Map.of("connectedProof",false,"performanceQualification",false,"scope","Offline replay of retained latest M session; same Gson serialization in all modes", "fromUtc",args[1],"levels",results);
        Files.writeString(Path.of(args[2]),new GsonBuilder().setPrettyPrinting().create().toJson(report)+"\n");System.out.println(gson.toJson(report));
    }
}
