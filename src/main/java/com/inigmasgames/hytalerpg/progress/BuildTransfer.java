package com.inigmasgames.hytalerpg.progress;

import com.google.gson.*;
import com.inigmasgames.hytalerpg.domain.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Stable content/topology and fixed layout only. Never accepts earned state, attributes or ownership. */
public record BuildTransfer(int schemaVersion,String layout,Map<String,String> skills,Map<String,String> passives,List<LinkEdge> edges){
    public static final int MAX_BYTES=16384;
    private static final Gson JSON=new Gson();
    public BuildTransfer {
        if(schemaVersion!=1||!"STATIC_SKILL_TREE_V1".equals(layout)||skills==null||passives==null||edges==null
                ||skills.size()>3||passives.size()>6||edges.size()>8)throw new IllegalArgumentException("INVALID_BUILD_TRANSFER");
        skills=Collections.unmodifiableMap(new TreeMap<>(skills));passives=Collections.unmodifiableMap(new TreeMap<>(passives));edges=List.copyOf(edges);
        skills.forEach((slot,id)->{if(!SkillSlot.parse(slot).externalId().equals(slot))throw new IllegalArgumentException("INVALID_SKILL_SLOT");AcquisitionProgress.id(id);});
        passives.forEach((slot,id)->{if(!PassiveSlot.parse(slot).externalId().equals(slot))throw new IllegalArgumentException("INVALID_PASSIVE_SLOT");AcquisitionProgress.id(id);});
    }
    public static BuildTransfer of(RpgPlayerState state){
        var skills=new TreeMap<String,String>();for(var slot:SkillSlot.values())state.skill(slot).ifPresent(id->skills.put(slot.externalId(),id.value()));
        var passives=new TreeMap<String,String>();for(var slot:PassiveSlot.values())state.passive(slot).ifPresent(id->passives.put(slot.externalId(),id.value()));
        return new BuildTransfer(1,"STATIC_SKILL_TREE_V1",skills,passives,state.linkEdges());
    }
    public String encode(){String json=JSON.toJson(this);if(json.getBytes(StandardCharsets.UTF_8).length>MAX_BYTES)throw new IllegalArgumentException("BUILD_TRANSFER_BUDGET");return json;}
    public static BuildTransfer decode(String json){
        if(json==null||json.getBytes(StandardCharsets.UTF_8).length>MAX_BYTES)throw new IllegalArgumentException("BUILD_TRANSFER_BUDGET");
        JsonElement raw=JsonParser.parseString(json);if(!raw.isJsonObject()||!raw.getAsJsonObject().keySet().equals(Set.of("schemaVersion","layout","skills","passives","edges")))throw new IllegalArgumentException("BUILD_TRANSFER_FIELDS");
        BuildTransfer result=JSON.fromJson(raw,BuildTransfer.class);
        if(!JSON.toJsonTree(result).equals(raw))throw new IllegalArgumentException("BUILD_TRANSFER_SHAPE");return result;
    }
    public void applyTo(RpgPlayerState state){
        state.equippedSkills=new String[3];state.equippedPassives=new String[6];state.inactivePassives.clear();
        skills.forEach((slot,id)->state.skill(SkillSlot.parse(slot),new SkillId(id)));
        passives.forEach((slot,id)->state.passive(PassiveSlot.parse(slot),new PassiveId(id)));state.linkEdges(edges);
    }
}
