package com.inigmasgames.hytalerpg.execution.hytale;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.asset.type.attitude.Attitude;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.asset.builder.SupportConfigBuilder;
import com.hypixel.hytale.server.npc.asset.builder.BuilderDescriptorState;
import com.hypixel.hytale.server.npc.role.support.WorldSupport;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Real pinned WorldSupport constructor/cache/getter/tick. Not a connected blackboard/NPC test. */
class Stage13NativeAttitudeCacheTest {
    static final Ref<EntityStore> NPC=new Ref<>(null,1),PLAYER=new Ref<>(null,2);
    static class NativeSupport extends WorldSupport {
        NativeSupport(){super(new SupportConfigBuilder<Object>(){
            @Override public Object build(BuilderSupport b){throw new AssertionError("Role construction is outside this cache fixture");}
            @Override public Class<Object> category(){return Object.class;}
            @Override public String getShortDescription(){return "Cache contract fixture";}
            @Override public String getLongDescription(){return getShortDescription();}
            @Override public BuilderDescriptorState getBuilderDescriptorState(){return BuilderDescriptorState.Stable;}
            @Override public boolean isEnabled(com.hypixel.hytale.server.npc.util.expression.ExecutionContext c){return true;}
            @Override public boolean excludeFromRegularBuilding(){return false;}
            @Override public Attitude getDefaultPlayerAttitude(BuilderSupport b){return Attitude.FRIENDLY;}
            @Override public Attitude getDefaultNPCAttitude(BuilderSupport b){return Attitude.NEUTRAL;}
            @Override public int getAttitudeGroup(BuilderSupport b){return -1;}
            @Override public int getItemAttitudeGroup(BuilderSupport b){return -1;}
        },null);}
        Object cache(){return attitudeCache;}
        int size(){return attitudeCache.size();}
        void seed(Attitude value){attitudeCache.put(PLAYER.getIndex(),value);}
    }
    @Test void coldNativeGetterReproducesExactCacheNullDereference(){
        var support=new NativeSupport();assertNull(support.cache());
        var error=assertThrows(NullPointerException.class,()->support.getAttitude(NPC,PLAYER,null));
        assertEquals(WorldSupport.class.getName(),error.getStackTrace()[0].getClassName());
        assertEquals("getAttitude",error.getStackTrace()[0].getMethodName());
        assertTrue(error.getMessage().contains("attitudeCache"),error.getMessage());
    }
    @Test void nativePreparationIsIdempotentAndDoesNotInventFriendship(){
        var support=new NativeSupport();assertSame(support,NativeNpcAttitudes.prepared(support));
        assertNotNull(support.cache());assertEquals(0,support.size());var cache=support.cache();
        for(var attitude:Attitude.values()){
            support.seed(attitude);assertSame(support,NativeNpcAttitudes.prepared(support));
            assertSame(cache,support.cache());
            // The actual installed native getter, not a reimplementation of attitude lookup.
            assertEquals(attitude,support.getAttitude(NPC,PLAYER,null));
        }
    }
    @Test void nativeTickStillExpiresCacheWithoutResettingItOnEveryRead(){
        var support=new NativeSupport();NativeNpcAttitudes.prepared(support);support.seed(Attitude.HOSTILE);
        support.tick(.05f);NativeNpcAttitudes.prepared(support);assertEquals(1,support.size());
        support.tick(.2f);assertEquals(0,support.size());
    }
    @Test void everyRpgNativeAttitudeReadRequestsCacheFirst() throws Exception {
        try(var files=Files.walk(Path.of("src/main/java"))){
            var reads=new ArrayList<String>();
            for(var file:files.filter(p->p.toString().endsWith(".java")).toList())
                for(var line:Files.readAllLines(file))if(line.contains(".getAttitude("))reads.add(line);
            assertEquals(3,reads.size());
            assertTrue(reads.stream().allMatch(line->line.contains("NativeNpcAttitudes.prepared(")));
        }
    }
}
