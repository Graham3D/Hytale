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
                var complete=new TreeMap<String,Object>();
                var balance=EnemyBalance.canonical();
                var immunity=new EnemyImmunitySelection(balance,EnemyAffinityRegistry.canonical());
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
                    var nativeChannels=new TreeSet<>(role.nativeImmunityChannels());
                    if(era==DifficultyId.HELL)com.inigmasgames.hytalerpg.difficulty.AuthoredEncounterCatalog.load().roles().stream()
                            .filter(a->a.id().equals(id)).forEach(a->a.hellImmunities().forEach(c->nativeChannels.add(c.canonical().name())));
                    var completeChoices=new TreeMap<String,String>();
                    boolean leaderAccepted=immunity.select(role.canonicalRoleId(),EnemyRarity.UNIQUE,era,false,nativeChannels,
                            Set.of(),List.of(),true,false,false,false,"coverage").accepted();
                    boolean minionAccepted=immunity.select(role.canonicalRoleId(),EnemyRarity.NORMAL,era,false,nativeChannels,
                            Set.of(),List.of(),true,false,false,false,"coverage-minion").accepted();
                    for(var decision:decisions.entrySet()){
                        String[] parts=decision.getKey().split("/");
                        var choice=new EnemyAffixSelection.Choice(parts[0],parts.length==1?null:EnemyAffixRegistry.Selector.valueOf(parts[1]));
                        boolean packbound=parts[0].equals("ME-024");
                        String reason=decision.getValue();
                        var grant=immunity.select(role.canonicalRoleId(),EnemyRarity.UNIQUE,era,false,nativeChannels,
                                Set.of(),List.of(choice),true,packbound,false,false,"coverage");
                        if(!grant.accepted())reason=grant.rejection();
                        if(!minionAccepted)reason="MINION_NATIVE_IMMUNITY";
                        if(packbound&&!immunity.select(role.canonicalRoleId(),EnemyRarity.NORMAL,era,false,nativeChannels,
                                Set.of(),List.of(),true,false,true,false,"coverage-guard").accepted())reason="GUARD_NATIVE_IMMUNITY";
                        var inheritance=registry.require(choice.affixId()).inheritance();
                        var canInherit=EnemyBirthPlanner.class.getDeclaredMethod("canInherit",EnemyAffixSelection.Binding.class,
                                String.class,EnemyAffixRegistry.Inheritance.class);
                        canInherit.setAccessible(true);
                        if(inheritance!=EnemyAffixRegistry.Inheritance.NONE&&inheritance!=EnemyAffixRegistry.Inheritance.PACK_AURA_MEMBERSHIP_ONLY
                                &&!(Boolean)canInherit.invoke(null,binding,choice.affixId(),inheritance))reason="MINION_INHERITANCE_BINDING";
                        completeChoices.put(decision.getKey(),reason);
                    }
                    complete.put(era.name(),Map.of("nativeImmunities",nativeChannels,"leaderAccepted",leaderAccepted,
                            "minionAccepted",minionAccepted,"choices",completeChoices,
                            "minionMinimum",balance.promotion().minionMinimum(),"minionMaximum",balance.promotion().minionMaximum(),
                            "maximumMembers",balance.promotion().maximumMembers()));
                }
                row.put("individualEligibility",eras);
                row.put("completeConstraints",complete);
                var minimumProofs=new TreeMap<String,Object>();
                if(bindings.productionEligibilityRejection(id).isEmpty())for(var era:DifficultyId.values()){
                    @SuppressWarnings("unchecked") var facts=(Map<String,Object>)complete.get(era.name());
                    if(!(Boolean)facts.get("leaderAccepted"))continue;
                    @SuppressWarnings("unchecked") var checked=(Map<String,String>)facts.get("choices");
                    var pool=checked.entrySet().stream().filter(e->e.getValue().equals("ELIGIBLE"))
                            .map(e->parseChoice(e.getKey())).toList();
                    @SuppressWarnings("unchecked") var nativeChannels=(Set<String>)facts.get("nativeImmunities");
                    var witness=minimumWitness(role,binding,era,pool,nativeChannels,selection,immunity);
                    assertNotNull(witness,id+"/"+era+": complete native minimum required");
                    minimumProofs.put(era.name(),witness);
                }
                row.put("completeMinimumWitnesses",minimumProofs);
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


    private static EnemyAffixSelection.Choice parseChoice(String key){
        String[] parts=key.split("/");return new EnemyAffixSelection.Choice(parts[0],parts.length==1?null:EnemyAffixRegistry.Selector.valueOf(parts[1]));
    }
    private static List<EnemyAffixSelection.Choice> minimumWitness(EnemyNativeBindings.Role role,
            EnemyAffixSelection.Binding binding,DifficultyId era,List<EnemyAffixSelection.Choice> pool,Set<String> nativeChannels,
            EnemyAffixSelection selection,EnemyImmunitySelection immunity) throws Exception {
        var names=com.google.gson.JsonParser.parseString(Files.readString(Path.of("src/main/resources/rpg/enemies/name-pools-v2.json"))).getAsJsonObject();
        for(var p:names.getAsJsonArray("prefixes"))for(var s:names.getAsJsonArray("suffixes")){
            if(p.getAsJsonObject().get("tier").getAsInt()!=1||s.getAsJsonObject().get("tier").getAsInt()!=1)continue;
            var choices=new ArrayList<EnemyAffixSelection.Choice>();
            choices.add(parseChoice(p.getAsJsonObject().get("signature").getAsString()));
            choices.add(parseChoice(s.getAsJsonObject().get("signature").getAsString()));
            var found=completeMinimum(role,binding,era,pool,nativeChannels,selection,immunity,choices,era.ordinal(),0);
            if(found!=null)return found;
        }
        return null;
    }
    private static List<EnemyAffixSelection.Choice> completeMinimum(EnemyNativeBindings.Role role,
            EnemyAffixSelection.Binding binding,DifficultyId era,List<EnemyAffixSelection.Choice> pool,Set<String> nativeChannels,
            EnemyAffixSelection selection,EnemyImmunitySelection immunity,List<EnemyAffixSelection.Choice> chosen,int left,int start){
        var request=new EnemyAffixSelection.Request(binding,era,EnemyRarity.UNIQUE,2+era.ordinal(),List.of(),true);
        if(!pool.containsAll(chosen)||!selection.legal(request,chosen))return null;
        if(left>0){
            for(int i=start;i<pool.size();i++){
                var next=new ArrayList<>(chosen);next.add(pool.get(i));
                var found=completeMinimum(role,binding,era,pool,nativeChannels,selection,immunity,next,left-1,i+1);
                if(found!=null)return found;
            }
            return null;
        }
        boolean horde=chosen.stream().anyMatch(c->c.affixId().equals("ME-022"));
        boolean packbound=chosen.stream().anyMatch(c->c.affixId().equals("ME-024"));
        for(int base=3;base<=5;base++){
            int minions=base+(horde?2:0);
            if(1+minions>EnemyBalance.canonical().promotion().maximumMembers()||packbound&&(minions<2||minions>5))return null;
            // Whole-set policy call, including sustain/guard constraints. Grant RNG cannot relax safety.
            for(int seed=0;seed<4;seed++){
                var leader=immunity.select(role.canonicalRoleId(),EnemyRarity.UNIQUE,era,false,nativeChannels,Set.of(),chosen,
                        true,packbound,false,false,"minimum/"+seed);
                var minion=immunity.select(role.canonicalRoleId(),EnemyRarity.NORMAL,era,false,nativeChannels,Set.of(),List.of(),
                        true,false,packbound,false,"minimum-minion/"+seed);
                if(!leader.accepted()||!minion.accepted())return null;
            }
        }
        return List.copyOf(chosen);
    }
    @Test void lockedSignaturesKeepExistingSemanticConflicts() {
        var role=EnemyNativeBindings.load().role("Skeleton_Scout").orElseThrow();
        var binding=role.affixBinding("audit",true,3);
        var request=new EnemyAffixSelection.Request(binding,DifficultyId.HELL,EnemyRarity.UNIQUE,4,List.of(),true);
        var selector=new EnemyAffixSelection(EnemyAffixRegistry.canonical());
        assertFalse(selector.legal(request,List.of(EnemyAffixSelection.Choice.of(EnemyAffixRegistry.Operator.MAGIC_RESISTANT),
                EnemyAffixSelection.Choice.of(EnemyAffixRegistry.Operator.STONE_SKIN))));
        assertTrue(selector.eligible(request,EnemyAffixSelection.Choice.of(EnemyAffixRegistry.Operator.REFLECTIVE)),
                "Pinned incoming Health route is independent of outgoing arrow conversion");
        assertFalse(role.actions().getFirst().supportedAffixIds().contains("ME-026"));
        assertFalse(selector.eligible(request,EnemyAffixSelection.Choice.of(EnemyAffixRegistry.Operator.FIRE_ENCHANTED)));
    }
}
