package com.inigmasgames.hytalerpg.difficulty;

import com.google.gson.Gson;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Installed-role bindings, not fuzzy names or inventory-drop completion. */
public record GolemMilestones(int schemaVersion, String profileId, List<Golem> golems) {
    public record Golem(String id, String label, String roleId, String assetPath, String assetSha256, String nativeMarker) {
        public Golem {
            if(id==null||!id.matches("golem\\.[a-z]+")||label==null||label.isBlank()||roleId==null||roleId.isBlank()
                    ||assetPath==null||!assetPath.startsWith("Server/NPC/Roles/")||assetSha256==null||!assetSha256.matches("[a-f0-9]{64}")||nativeMarker==null)
                throw new IllegalArgumentException("INVALID_GOLEM_BINDING");
        }
    }
    public static final Set<String> REQUIRED_V1=Set.of("golem.earth","golem.flame","golem.frost","golem.sand","golem.thunder");
    public GolemMilestones {
        golems=List.copyOf(golems);
        if(schemaVersion!=1||!"rpg.golem-milestones.v1".equals(profileId)||golems.size()!=5
                ||!golems.stream().map(Golem::id).collect(java.util.stream.Collectors.toSet()).equals(REQUIRED_V1)
                ||golems.stream().map(Golem::roleId).distinct().count()!=5)throw new IllegalArgumentException("GOLEM_CATALOG_INCOMPLETE");
    }
    public Golem require(String id){return golems.stream().filter(g->g.id().equals(id)||g.id().equals("golem."+id)).findFirst().orElseThrow(()->new IllegalArgumentException("UNKNOWN_GOLEM"));}
    public Optional<Golem> role(String role){return golems.stream().filter(g->g.roleId().equals(role)).findFirst();}
    public boolean complete(DifficultyProgress progress,DifficultyId mode){return progress.milestones().get(mode).containsAll(REQUIRED_V1);}
    public String rejection(DifficultyProgress progress,DifficultyId target){
        if(target==DifficultyId.NORMAL)return "";
        var previous=DifficultyId.values()[target.ordinal()-1];
        if(!progress.unlocked(target)||!complete(progress,previous))return "Complete "+previous+" golems: "+golems.stream().filter(g->!progress.milestones().get(previous).contains(g.id())).map(Golem::label).toList();
        return "";
    }
    public static GolemMilestones load(){
        try(var stream=GolemMilestones.class.getResourceAsStream("/rpg/progression/golem-milestones.json")){
            if(stream==null)throw new IllegalStateException("MISSING_GOLEM_BINDINGS");
            return new Gson().fromJson(new InputStreamReader(stream,StandardCharsets.UTF_8),GolemMilestones.class);
        }catch(java.io.IOException error){throw new IllegalStateException(error);}
    }
}
