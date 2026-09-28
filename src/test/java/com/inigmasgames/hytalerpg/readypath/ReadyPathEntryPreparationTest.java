package com.inigmasgames.hytalerpg.readypath;

import com.inigmasgames.hytalerpg.content.RpgCatalog;
import com.inigmasgames.hytalerpg.domain.*;
import com.inigmasgames.hytalerpg.links.*;
import com.inigmasgames.hytalerpg.progress.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class ReadyPathEntryPreparationTest {
    static class Repo implements RpgPlayerStateRepository {
        final AtomicInteger loads = new AtomicInteger();
        volatile CountDownLatch hold;
        volatile boolean fail;
        public LoadResult load(UUID player) {
            loads.incrementAndGet();
            if (hold != null) try { if(!hold.await(10, TimeUnit.SECONDS)) throw new AssertionError("Fixture load timeout"); }
            catch (InterruptedException e) { throw new RuntimeException(e); }
            if (fail) throw new IllegalStateException("injected corrupt authority");
            var state=RpgPlayerState.create(player);
            state.learnedSkills.add("quick_slash");state.skill(SkillSlot.SKILL01,new SkillId("quick_slash"));
            return new LoadResult(state,true,false,RpgPlayerState.CURRENT_SCHEMA,List.of());
        }
        public void save(RpgPlayerState state) {}
    }
    static RpgLoadoutService authority(RpgCatalog catalog, RpgPlayerStateRepository repo) {
        var compatibility=new CompatibilityService();var graph=new RpgLinkGraphService(catalog,compatibility);
        return new RpgLoadoutService(catalog,repo,graph,new LinkCompiler(catalog,graph,compatibility),
                new OwnershipEntitlementPolicy(false),record->{});
    }
    @ParameterizedTest @ValueSource(ints={1,4,8,16})
    void concurrentPreparationHydratesAndCompilesExactlyOncePerPlayer(int count) throws Exception {
        var repo=new Repo();
        try(var loadouts=authority(RpgCatalog.loadCanonical(),repo);var prep=new PlayerEntryPreparation(loadouts)) {
            var world=UUID.randomUUID();var futures=new ArrayList<CompletableFuture<PlayerEntryPreparation.PlayerEntryView>>();
            for(int i=0;i<count;i++)futures.add(prep.prepare(UUID.randomUUID(),world,"image-a"));
            for(var future:futures){var view=future.get(10,TimeUnit.SECONDS);
                assertTrue(prep.current(view.token()));assertEquals(world,view.token().world());
                assertFalse(view.loadout().plans().isEmpty());assertTrue(loadouts.ready(view.token().player()));
                assertEquals(1,loadouts.presentationCompilations(view.token().player()));
                assertSame(view.loadout().plans().get(SkillSlot.SKILL01),loadouts.getPresentationView(view.token().player()).plans().get(SkillSlot.SKILL01));
            }
            assertEquals(count,repo.loads.get());
        }
    }
    @Test void reconnectRejectsOldCompletionAndSharesAuthoritativeLoad() throws Exception {
        var repo=new Repo();repo.hold=new CountDownLatch(1);
        try(var loadouts=authority(RpgCatalog.loadCanonical(),repo);var prep=new PlayerEntryPreparation(loadouts)) {
            UUID player=UUID.randomUUID(),newWorld=UUID.randomUUID();
            var old=prep.prepare(player,UUID.randomUUID(),"image-a");
            var current=prep.prepare(player,newWorld,"image-b");
            assertTrue(old.isCompletedExceptionally());repo.hold.countDown();
            var view=current.get(10,TimeUnit.SECONDS);
            assertEquals(newWorld,view.token().world());assertEquals("image-b",view.token().contentRevision());
            assertTrue(prep.current(view.token()));assertEquals(1,repo.loads.get());
            view.loadout().state().attributes.clear();
            assertFalse(loadouts.getPresentationView(player).state().attributes.isEmpty());
        } finally {repo.hold.countDown();}
    }
    @Test void disconnectAndShutdownRejectLateResultsWithoutCancellingDurableOwner() throws Exception {
        var repo=new Repo();repo.hold=new CountDownLatch(1);
        try(var loadouts=authority(RpgCatalog.loadCanonical(),repo)) {
            var prep=new PlayerEntryPreparation(loadouts);
            UUID player=UUID.randomUUID();var future=prep.prepare(player,UUID.randomUUID(),"a");
            prep.detach(player);assertTrue(future.isCompletedExceptionally());prep.close();
            assertTrue(prep.prepare(player,UUID.randomUUID(),"a").isCompletedExceptionally());
            repo.hold.countDown();loadouts.preload(player).toCompletableFuture().get(10,TimeUnit.SECONDS);
            assertTrue(loadouts.ready(player)); // Detach revokes publication, not accepted repository work.
        } finally {repo.hold.countDown();}
    }
    @Test void corruptAuthorityCannotPublishAReadyView() throws Exception {
        var repo=new Repo();repo.fail=true;var loadouts=authority(RpgCatalog.loadCanonical(),repo);
        try(var prep=new PlayerEntryPreparation(loadouts)) {
            UUID player=UUID.randomUUID(),world=UUID.randomUUID();
            assertThrows(ExecutionException.class,()->prep.prepare(player,world,"a").get(10,TimeUnit.SECONDS));
            assertFalse(loadouts.ready(player));assertTrue(prep.prepared(player,world).isEmpty());
        } finally {assertThrows(IllegalStateException.class,loadouts::close,"Corruption must remain visible at shutdown");}
    }
    @Test void preparationQueueIsBoundedAndDoesNotRunRejectedWork() throws Exception {
        var hold=new CountDownLatch(1);var started=new CountDownLatch(1);
        try(var budget=new WorkBudget(1,1)) {
            var first=budget.submit(()->{started.countDown();try{hold.await();}catch(InterruptedException e){throw new RuntimeException(e);}return 1;});
            assertTrue(started.await(2,TimeUnit.SECONDS));var second=budget.submit(()->2);
            var rejected=budget.submit(()->{fail("Rejected work ran");return 3;});
            assertThrows(ExecutionException.class,()->rejected.get(2,TimeUnit.SECONDS));assertEquals(1,budget.queued());
            hold.countDown();assertEquals(1,first.get(2,TimeUnit.SECONDS));assertEquals(2,second.get(2,TimeUnit.SECONDS));
        } finally {hold.countDown();}
    }
    @Test void canonicalImageIsSharedAndRevisionIsStable() {
        var image=RpgCatalog.prepared();assertSame(image.catalog(),RpgCatalog.loadCanonical());
        assertSame(image,RpgCatalog.prepared());assertEquals(64,image.revision().length());
        assertThrows(UnsupportedOperationException.class,()->image.catalog().skills().clear());
    }
}
