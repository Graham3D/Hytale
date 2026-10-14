package com.inigmasgames.hytalerpg.enemies;

import com.google.gson.GsonBuilder;
import com.inigmasgames.hytalerpg.difficulty.DifficultyId;
import com.inigmasgames.hytalerpg.progress.EnemyRewardRegistry;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Offline inputs for the Stage 1 audit. Never a runtime registry or v2 sampler. */
class Me12CoverageInputsTest {
    @Test void exportActualEligibilityWithoutBroadeningAnyNativeCertificate() throws Exception {
        var bindings=EnemyNativeBindings.load();
        var registry=EnemyAffixRegistry.canonical();
        var selection=new EnemyAffixSelection(registry);
        var catalog=EnemyRewardRegistry.load();
        var roles=new TreeSet<String>();
        catalog.roles().forEach(r->roles.add(r.roleId()));
        catalog.aliases().forEach(r->roles.add(r.roleId()));
        var rows=new ArrayList<Map<String,Object>>();
        for(var id:roles){
            var row=new TreeMap<String,Object>();
            row.put("roleId",id);
            row.put("canonicalRoleId",catalog.resolveRole(id).orElseThrow().canonical().roleId());
            row.put("productionRejection",bindings.productionEligibilityRejection(id).orElse(""));
            var role=bindings.role(id).orElse(null);
            row.put("hasBinding",role!=null);
            if(role!=null){
                row.put("role",role);
                var binding=role.affixBinding(bindings.revision(),
                        role.nativeImmunityChannels().stream().filter(c->!c.equals("PHYSICAL")).count()<6,3);
                var eras=new TreeMap<String,Object>();
                for(var era:DifficultyId.values()){
                    var decisions=new TreeMap<String,String>();
                    for(var op:EnemyAffixRegistry.Operator.values()){
                        var choices=op==EnemyAffixRegistry.Operator.AURA_ENCHANTED
                                ? Arrays.stream(EnemyAffixRegistry.Selector.values())
                                  .map(s->new EnemyAffixSelection.Choice(op.id(),s)).toList()
                                : List.of(EnemyAffixSelection.Choice.of(op));
                        for(var choice:choices){
                            String reason=selection.rejectionReason(binding,EnemyRarity.UNIQUE,choice).orElse("ELIGIBLE");
                            if(registry.require(op).weight(era)<=0)reason="ZERO_WEIGHT";
                            if(choice.selector()!=null&&registry.require(op).selectorWeight(choice.selector())<=0)reason="ZERO_SELECTOR_WEIGHT";
                            decisions.put(choice.affixId()+(choice.selector()==null?"":"/"+choice.selector()),reason);
                        }
                    }
                    eras.put(era.name(),decisions);
                }
                row.put("individualEligibility",eras);
            }
            rows.add(row);
        }
        assertEquals(275,rows.size(),"Roster changes require explicit audit reconciliation");
        assertTrue(rows.stream().anyMatch(r->r.get("roleId").equals("Skeleton_Scout")));
        var output=Path.of("build/reports/me12/coverage-inputs.json");
        Files.createDirectories(output.getParent());
        var semanticGroups=new TreeMap<String,Object>();
        for(var op:EnemyAffixRegistry.Operator.values()){
            if(op==EnemyAffixRegistry.Operator.AURA_ENCHANTED){
                for(var s:EnemyAffixRegistry.Selector.values())
                    semanticGroups.put(op.id()+"/"+s,EnemyAffixSelection.semanticGroups(op,s));
            }else semanticGroups.put(op.id(),EnemyAffixSelection.semanticGroups(op,null));
        }
        Files.writeString(output,new GsonBuilder().setPrettyPrinting().create().toJson(
                Map.of("bindingRevision",bindings.revision(),"initialMinions",3,"rows",rows,"semanticGroups",semanticGroups))+"\n");
    }

    @Test void lockedSignaturesKeepExistingSemanticConflicts() {
        var role=EnemyNativeBindings.load().role("Skeleton_Scout").orElseThrow();
        var binding=role.affixBinding("audit",true,3);
        var request=new EnemyAffixSelection.Request(binding,DifficultyId.HELL,EnemyRarity.UNIQUE,4,List.of(),true);
        var selector=new EnemyAffixSelection(EnemyAffixRegistry.canonical());
        assertFalse(selector.legal(request,List.of(EnemyAffixSelection.Choice.of(EnemyAffixRegistry.Operator.MAGIC_RESISTANT),
                EnemyAffixSelection.Choice.of(EnemyAffixRegistry.Operator.STONE_SKIN))));
        assertFalse(selector.eligible(request,EnemyAffixSelection.Choice.of(EnemyAffixRegistry.Operator.REFLECTIVE)),
                "Investigation must not silently certify a route");
    }
}
