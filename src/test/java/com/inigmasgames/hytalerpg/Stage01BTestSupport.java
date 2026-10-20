package com.inigmasgames.hytalerpg;

import com.inigmasgames.hytalerpg.content.RpgCatalog;
import com.inigmasgames.hytalerpg.diagnostics.RpgSkillTracer;
import com.inigmasgames.hytalerpg.diagnostics.RpgTraceRecord;
import com.inigmasgames.hytalerpg.links.CompatibilityService;
import com.inigmasgames.hytalerpg.links.LinkCompiler;
import com.inigmasgames.hytalerpg.links.RpgLinkGraphService;
import com.inigmasgames.hytalerpg.progress.OwnershipEntitlementPolicy;
import com.inigmasgames.hytalerpg.progress.RpgLoadoutService;
import com.inigmasgames.hytalerpg.progress.RpgPlayerState;
import com.inigmasgames.hytalerpg.progress.RpgPlayerStateRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class Stage01BTestSupport {
    static Bundle bundle() { return bundle(new InMemoryRepository(), new RecordingTracer()); }
    static Bundle bundle(InMemoryRepository repository, RpgSkillTracer tracer) {
        RpgCatalog catalog = RpgCatalog.loadCanonical();
        CompatibilityService compatibility = new CompatibilityService();
        RpgLinkGraphService graph = new RpgLinkGraphService(catalog, compatibility);
        LinkCompiler compiler = new LinkCompiler(catalog, graph, compatibility);
        RpgLoadoutService service = new RpgLoadoutService(catalog, repository, graph, compiler,
                new OwnershipEntitlementPolicy(true), tracer);
        return new Bundle(catalog, graph, compiler, repository, tracer, service);
    }

    record Bundle(RpgCatalog catalog, RpgLinkGraphService graph, LinkCompiler compiler,
                  InMemoryRepository repository, RpgSkillTracer tracer, RpgLoadoutService service) {}

    static final class InMemoryRepository implements RpgPlayerStateRepository {
        final Map<UUID, RpgPlayerState> states = new ConcurrentHashMap<>();
        boolean failSave;
        int saves;
        @Override public LoadResult load(UUID playerUuid) {
            RpgPlayerState state = states.get(playerUuid);
            return state == null ? new LoadResult(RpgPlayerState.create(playerUuid), false, false,
                    RpgPlayerState.CURRENT_SCHEMA, List.of())
                    : new LoadResult(state.copy(), true, false, state.schemaVersion, List.of());
        }
        @Override public void save(RpgPlayerState state) {
            if (failSave) throw new IllegalStateException("injected save failure");
            states.put(state.playerUuid(), state.copy()); saves++;
        }
    }

    static final class RecordingTracer implements RpgSkillTracer {
        final List<RpgTraceRecord> records = new ArrayList<>();
        @Override public void trace(RpgTraceRecord record) { records.add(record); }
    }
}
