package com.inigmasgames.hytalerpg.readypath;

import com.inigmasgames.hytalerpg.content.RpgCatalog;
import com.inigmasgames.hytalerpg.domain.*;
import com.inigmasgames.hytalerpg.execution.*;
import com.inigmasgames.hytalerpg.progress.*;
import org.junit.jupiter.api.Test;
import java.lang.management.ManagementFactory;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Valid isolated definitions/profiles. No fixture asset or ID enters production resources. */
class ReadyPathContentGrowthTest {
    @SuppressWarnings("unchecked") static <T extends Record> T copy(T original,Map<String,Object> replacements) throws Exception {
        var components=original.getClass().getRecordComponents();var types=new Class<?>[components.length];var values=new Object[components.length];
        for(int i=0;i<components.length;i++){types[i]=components[i].getType();values[i]=replacements.containsKey(components[i].getName())
                ?replacements.get(components[i].getName()):components[i].getAccessor().invoke(original);}
        return (T)original.getClass().getDeclaredConstructor(types).newInstance(values);
    }
    static RpgCatalog grown(int scale) throws Exception {
        var source=RpgCatalog.loadCanonical();var skills=new ArrayList<>(source.skills());var passives=new ArrayList<>(source.passives());
        var originalProfiles=Stage04SkillProfiles.loadCanonical(source);var profiles=new ArrayList<>(originalProfiles.all().values());
        for(int factor=1;factor<scale;factor++) {
            for(var skill:source.skills()) {
                String id="readypath_qa_"+factor+"_"+skill.id().value();
                skills.add(copy(skill,Map.of("id",new SkillId(id),"name","QA "+factor+" "+skill.name(),"aliases",List.of(id))));
                profiles.add(copy(originalProfiles.require(skill.id().value()),Map.of("skillId",id)));
            }
            for(var passive:source.passives()) {
                String id="readypath_qa_"+factor+"_"+passive.id().value();
                passives.add(copy(passive,Map.of("id",new PassiveId(id),"name","QA "+factor+" "+passive.name(),"aliases",List.of(id))));
            }
        }
        var result=new RpgCatalog(skills,passives);var runtime=new Stage04SkillProfiles(profiles);
        assertEquals(97*scale,result.skills().size());assertEquals(67*scale,result.passives().size());
        assertEquals(97*scale,runtime.all().size());for(var skill:result.skills())assertNotNull(runtime.require(skill.id().value()));
        return result;
    }
    @Test void unusedDefinitionGrowthDoesNotRecompileOrChangeTheActiveBuild() throws Exception {
        var mx=ManagementFactory.getThreadMXBean();var allocations=mx instanceof com.sun.management.ThreadMXBean a?a:null;
        var services=new LinkedHashMap<Integer,RpgLoadoutService>();var players=new HashMap<Integer,UUID>();
        String expected=null;
        try {
            for(int scale:List.of(1,2,4)) {
                long begin=System.nanoTime();var catalog=grown(scale);long shared=System.nanoTime()-begin;
                var service=ReadyPathEntryPreparationTest.authority(catalog,new ReadyPathEntryPreparationTest.Repo());services.put(scale,service);
                UUID player=UUID.randomUUID();players.put(scale,player);service.preload(player).toCompletableFuture().join();
                var view=service.getPresentationView(player);String hash=view.plans().get(SkillSlot.SKILL01).planHash();
                if(expected==null)expected=hash;else assertEquals(expected,hash);
                for(int i=0;i<2000;i++)service.getPresentationView(player);
                System.out.printf(Locale.ROOT,"READYPATH_SHARED scale=%d skills=%d passives=%d buildMs=%.3f%n",scale,catalog.skills().size(),catalog.passives().size(),shared/1e6);
            }
            // Alternating forward/reverse batches retain every sample. This is server view CPU, not Join-to-play.
            for(int round=0;round<40;round++)for(int scale:round%2==0?List.of(1,2,4):List.of(4,2,1)) {
                var service=services.get(scale);UUID player=players.get(scale);long thread=Thread.currentThread().threadId();
                long cpu=mx.isCurrentThreadCpuTimeSupported()?mx.getCurrentThreadCpuTime():-1;
                long bytes=allocations!=null&&allocations.isThreadAllocatedMemoryEnabled()?allocations.getThreadAllocatedBytes(thread):-1;
                long begin=System.nanoTime();
                for(int i=0;i<100000;i++)service.getPresentationView(player);
                long wall=System.nanoTime()-begin;
                long cpuDelta=cpu<0?-1:mx.getCurrentThreadCpuTime()-cpu;
                long byteDelta=bytes<0?-1:allocations.getThreadAllocatedBytes(thread)-bytes;
                assertEquals(1,service.presentationCompilations(player));
                System.out.printf(Locale.ROOT,"READYPATH_GROWTH scale=%d round=%d reads=100000 wallNanos=%d cpuNanos=%d allocatedBytes=%d clientMs=UNKNOWN%n",scale,round,wall,cpuDelta,byteDelta);
            }
        } finally {for(var service:services.values())service.close();}
    }
}
