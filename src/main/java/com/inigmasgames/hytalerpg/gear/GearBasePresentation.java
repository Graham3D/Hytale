package com.inigmasgames.hytalerpg.gear;

import com.google.gson.Gson;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Authored presentation vocabulary keyed by GearCatalog families, not item ID guesses. */
public final class GearBasePresentation {
    private record Labels(int schemaVersion, Map<String,String> families) { }
    private static final GearCatalog CATALOG=GearCatalog.load();
    private static final Map<String,String> FAMILIES=load();
    private GearBasePresentation() { }
    private static Map<String,String> load() {
        try(var stream=GearBasePresentation.class.getResourceAsStream("/rpg/gear/tooltip-families-v1.json")) {
            if(stream==null)throw new IllegalStateException("Missing gear tooltip family labels");
            var labels=new Gson().fromJson(new InputStreamReader(stream,StandardCharsets.UTF_8),Labels.class);
            if(labels.schemaVersion()!=1||labels.families()==null)throw new IllegalStateException("Invalid gear tooltip family labels");
            for(var base:CATALOG.bases())if(!labels.families().containsKey(base.family()))
                throw new IllegalStateException("Missing tooltip classification for "+base.family());
            return Map.copyOf(labels.families());
        }catch(java.io.IOException failure){throw new java.io.UncheckedIOException(failure);}
    }
    public static String classification(String baseId) { return FAMILIES.get(CATALOG.base(baseId).family()); }
}
