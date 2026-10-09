package com.inigmasgames.hytalerpg.gear;

import com.google.gson.Gson;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class GearBindings {
    public record Binding(String baseId,String nativeItemId,String managedItemId,String disposition,String reason,
                          String sourceAsset,String sourceSha256,String resolutionClass) {
        public boolean mapped() { return "MAPPED".equals(disposition); }
        public String carrier(GearRarity rarity) {
            return managedItemId + (rarity.quality()==GearQuality.NORMAL ? "" : "_" + rarity.nativeParticleTier);
        }
    }
    private record Data(int schemaVersion,String assetsSha256,List<Binding> bindings) {}
    private final Map<String,Binding> bindings;
    public GearBindings() {
        try(var input=getClass().getResourceAsStream("/rpg/gear/native-bindings-v1.json")) {
            if(input==null) throw new IllegalStateException("Missing gear bindings");
            var data=new Gson().fromJson(new InputStreamReader(input,StandardCharsets.UTF_8),Data.class);
            if(data.schemaVersion()!=1 || data.bindings().size()!=447) throw new IllegalStateException("Incomplete gear bindings");
            var rows=new HashMap<String,Binding>();
            for(var row:data.bindings()) {
                if(rows.put(row.baseId(),row)!=null || row.reason()==null || row.reason().isBlank()
                        || !Set.of("RESOLVABLE_AUDIT","VALIDATED_NATIVE_REUSE","MISSING_ADAPTER","TRUE_NATIVE_CAPABILITY_BLOCKER","INTENTIONALLY_UNSUPPORTED").contains(row.resolutionClass())
                        || row.mapped() && (row.nativeItemId()==null || row.managedItemId()==null || !"VALIDATED_NATIVE_REUSE".equals(row.resolutionClass()))
                        || !row.mapped() && row.managedItemId()!=null)
                    throw new IllegalStateException("Invalid gear binding "+row.baseId());
            }
            bindings=Collections.unmodifiableMap(new TreeMap<>(rows));
        } catch(IOException e) { throw new UncheckedIOException(e); }
    }
    public Binding require(String base) {
        var b=bindings.get(base); if(b==null) throw new IllegalArgumentException("No binding for "+base); return b;
    }
    /** Stable order for native-id registration, package audits and diagnostics. */
    public Collection<Binding> all() { return bindings.values(); }
}
