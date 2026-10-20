package com.inigmasgames.hytalerpg.progress;

import com.inigmasgames.hytalerpg.content.RpgCatalog;
import com.inigmasgames.hytalerpg.domain.*;
import com.inigmasgames.hytalerpg.execution.*;
import com.inigmasgames.hytalerpg.links.*;
import java.util.*;

/** Load-time content recovery only. Never called to make an invalid player edit succeed. */
public final class InactivePassiveRecovery {
    private final RpgCatalog catalog;
    private final LinkCompiler compiler;
    private final RpgLinkGraphService graph;
    private final Stage04SkillProfiles profiles;
    private final CompiledProfileResolver resolver=new CompiledProfileResolver();
    public InactivePassiveRecovery(RpgCatalog catalog,LinkCompiler compiler,RpgLinkGraphService graph){
        this.catalog=catalog;this.compiler=compiler;this.graph=graph;profiles=Stage04SkillProfiles.loadCanonical(catalog);
    }
    public boolean reconcile(RpgPlayerState state){
        var structure=graph.validateStructure(state);
        if(!structure.valid())throw new IllegalArgumentException("Malformed graph cannot use content recovery");
        var linked=structure.routes().keySet().stream().sorted().toList();
        // Six nodes => at most64 candidates. Maximal valid subset preserves introduced dependencies.
        RpgPlayerState best=null;int bestCount=-1;
        for(int mask=0;mask<(1<<linked.size());mask++){
            int count=Integer.bitCount(mask);if(count<=bestCount)continue;
            var trial=state.copy();trial.inactivePassives.clear();
            for(var slot:PassiveSlot.values())if(trial.passive(slot).filter(id->catalog.passive(id).isEmpty()).isPresent())
                trial.inactivePassives.put(slot.externalId(),"UNKNOWN_PASSIVE");
            for(int i=0;i<linked.size();i++)if((mask&(1<<i))==0)trial.inactivePassives.put(linked.get(i).externalId(),"INACTIVE_RECOVERY_CANDIDATE");
            // Unknown content must not inflate the retained-active count.
            if(linked.stream().anyMatch(slot->trial.inactive(slot)&&!trial.inactivePassives.get(slot.externalId()).equals("INACTIVE_RECOVERY_CANDIDATE")))continue;
            if(failure(trial).isEmpty()){best=trial;bestCount=count;}
        }
        if(best==null)throw new IllegalStateException("No safe passive subset for structurally valid saved graph");
        for(var slot:linked)if(best.inactive(slot)){
            var trial=best.copy();trial.inactivePassives.remove(slot.externalId());
            String reason=failure(trial);
            best.inactivePassives.put(slot.externalId(),reason.isEmpty()?"CONFLICTING_MODIFIER:MAXIMAL_VALID_SUBSET":reason);
        }
        boolean changed=!state.inactivePassives.equals(best.inactivePassives);
        state.inactivePassives=new LinkedHashMap<>(best.inactivePassives);return changed;
    }
    private String failure(RpgPlayerState state){
        var compiled=compiler.compile(state);
        if(!compiled.success())return bounded(compiled.code()+":"+compiled.message());
        for(var plan:compiled.plans().values()){
            var profile=profiles.all().get(plan.skillId().value());if(profile==null||plan.degraded())continue;
            try{resolver.resolve(profile,plan);}catch(IllegalArgumentException invalid){return bounded("UNSUPPORTED_COMPONENT:"+invalid.getMessage());}
        }
        return "";
    }
    private static String bounded(String reason){return reason.substring(0,Math.min(1024,reason.length()));}
    /** A changed route/content is a new explicit edit and must pass normal strict validation. */
    public void revalidateChanged(RpgPlayerState prior,RpgPlayerState next){
        var before=graph.validateStructure(prior);var after=graph.validateStructure(next);
        if(!before.valid()||!after.valid()){next.inactivePassives.clear();return;}
        for(var slot:PassiveSlot.values()){
            var oldRoute=before.routes().get(slot);var newRoute=after.routes().get(slot);
            boolean changed=!Objects.equals(oldRoute,newRoute)||!prior.passive(slot).equals(next.passive(slot));
            if(!changed&&newRoute!=null){var skill=newRoute.getLast().skillSlot();changed=!prior.skill(skill).equals(next.skill(skill));}
            if(changed||newRoute==null&&next.passive(slot).flatMap(catalog::passive).isPresent())next.inactivePassives.remove(slot.externalId());
        }
    }
}
