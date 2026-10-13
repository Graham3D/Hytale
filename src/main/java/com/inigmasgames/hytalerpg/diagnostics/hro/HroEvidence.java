package com.inigmasgames.hytalerpg.diagnostics.hro;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

/** Worker-only canonical durable evidence. Stable opaque subject supports explicit offline recovery joins. */
final class HroEvidence {
    private HroEvidence() { }
    static HroEvent normalize(HroEvent event) {
        if(!event.contract().equals("PERSISTENCE.RECOVERY"))return event;
        var f=new TreeMap<>(event.facts());
        var state=new TreeMap<String,String>();
        if(!f.containsKey("durableSubject"))return event;
        if(!f.get("durableSubject").startsWith("sha256:"))f.put("durableSubject","sha256:"+hash(event.provider()+"/"+f.get("durableSubject")));
        if(!f.containsKey("fingerprint")) {
        for(String key:List.of("schema","revision","generation","activation","phase","capacity","remaining","manaReceipt","cooldownReceipt","manaPaid")){
            if(!f.containsKey(key))return event;state.put(key,f.get(key));
        }
        f.put("fingerprint",hash(state.toString()));
        }
        return new HroEvent(event.schemaVersion(),event.runId(),event.sequence(),event.utcMillis(),event.monotonicNanos(),
                event.contract(),event.provider(),event.boundary(),event.operationId(),event.worldSegmentId(),event.connectionId(),event.nativeTick(),event.stateRevision(),f);
    }
    private static String hash(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}
        catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}}
}
