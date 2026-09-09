package com.inigmasgames.hytalerpg;

import com.google.gson.GsonBuilder;
import com.inigmasgames.hytalerpg.content.RpgCatalog;
import com.inigmasgames.hytalerpg.domain.*;
import com.inigmasgames.hytalerpg.execution.*;
import com.inigmasgames.hytalerpg.links.*;
import com.inigmasgames.hytalerpg.progress.RpgPlayerState;
import java.util.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Generated evidence is compiler/profile structure only, never native execution or connected proof. */
class Stage11CompatibilityMatrixTest {
    final RpgCatalog catalog=RpgCatalog.loadCanonical();
    final CompatibilityService compatibility=new CompatibilityService();
    final RpgLinkGraphService graph=new RpgLinkGraphService(catalog,compatibility);
    final LinkCompiler compiler=new LinkCompiler(catalog,graph,compatibility);
    final Stage04SkillProfiles profiles=Stage04SkillProfiles.loadCanonical(catalog);
    final CompiledProfileResolver resolver=new CompiledProfileResolver();
    final List<SkillDefinition> skills=catalog.skills().stream().sorted(Comparator.comparing(s->s.id().value())).toList();
    final List<PassiveDefinition> passives=catalog.passives().stream().sorted(Comparator.comparing(p->p.id().value())).toList();
    final Path output=Path.of("build","stage11-matrix");
    record Result(String gate,String detail,CompiledSkillPlan plan){}
    RpgPlayerState state(SkillDefinition skill,List<PassiveDefinition> selected,boolean joints){
        var s=RpgPlayerState.create(new UUID(0,1));s.skill(SkillSlot.SKILL01,skill.id());var edges=new ArrayList<LinkEdge>();
        for(int i=0;i<selected.size();i++){
            var slot=PassiveSlot.values()[i];s.passive(slot,selected.get(i).id());
            edges.add(new LinkEdge(1,new EdgeId("matrix-"+i),LinkNodeId.valueOf(slot.name()),joints?(i<3?LinkNodeId.JOINT01:LinkNodeId.JOINT02):LinkNodeId.SKILL01));
        }
        if(joints&&!selected.isEmpty())edges.add(new LinkEdge(1,new EdgeId("matrix-j1"),LinkNodeId.JOINT01,LinkNodeId.SKILL01));
        if(joints&&selected.size()>3)edges.add(new LinkEdge(1,new EdgeId("matrix-j2"),LinkNodeId.JOINT02,LinkNodeId.SKILL01));
        s.linkEdges(edges);return s;
    }
    Result assess(SkillDefinition skill,List<PassiveDefinition> selected,boolean joints){
        var c=compiler.compile(state(skill,selected,joints));
        if(!c.success()){assertNotEquals(ValidationCode.ACCEPTED,c.code());assertFalse(c.message().isBlank());return new Result("REJECTED",c.code().name(),null);}
        var p=c.plans().get(SkillSlot.SKILL01);assertNotNull(p);assertFalse(p.degraded());
        var profile=profiles.all().get(skill.id().value());
        if(profile==null)return new Result("CATALOG_ELIGIBLE_RUNTIME_NOT_IMPLEMENTED","STAGE04_05_REMAINDER",p);
        try{
            var resolved=resolver.resolve(profile,p);
            String gate=resolved.cage()!=null?com.inigmasgames.hytalerpg.execution.summon.SelectiveCageProfile.BLOCKED_BOUNDARY
                    :resolved.projectile()!=null?resolved.projectile().details().nativeCapabilityGate():"";
            return new Result(gate.isEmpty()?"COMPILED_PROFILE_RESOLVED_CONNECTED_UNVERIFIED":"COMPILED_PROFILE_WITH_EXPLICIT_RUNTIME_GATE",gate,p);
        }
        catch(RuntimeException invalid){Throwable cause=invalid;while(cause.getCause()!=null)cause=cause.getCause();return new Result("PROFILE_GATE",cause.getClass().getSimpleName()+":"+cause.getMessage(),p);}
    }
    void write(String file,Object value)throws Exception{Files.createDirectories(output);Files.writeString(output.resolve(file),new GsonBuilder().setPrettyPrinting().create().toJson(value));}
    @Test void lingeringNativeOrbitUsesFiniteSamplingNotDamagePulseBudget(){
        var h=new Stage08ConnectionTest.Harness("orbiting_shadow_blades","lingering");var fixture=new Stage11OrbitConversionTest();
        fixture.cover(h);fixture.cast(h);assertEquals(14,fixture.c(h).profile().connection().lifetimeSeconds());
        assertEquals(.05,fixture.c(h).profile().connection().intervalSeconds());assertEquals(4,fixture.c(h).profile().connection().details().bladeCount());
        fixture.to(h,13);assertEquals(1,h.runtime.size());fixture.to(h,14);assertEquals(0,h.runtime.size());assertEquals(1,h.resourceWrites);
        assertEquals(19,h.hits.size(),"One hit per victim every.75s; 280 contact samples are not 280 damage hits");
    }
    @Test void orbitSamplingAndDamagePulseCapsRemainDistinctAndFinite(){
        var p=profiles.require("orbiting_shadow_blades").connection();
        assertEquals(512,com.inigmasgames.hytalerpg.execution.connection.ConnectionProfile.MAX_ORBIT_SAMPLES);
        assertEquals(256,com.inigmasgames.hytalerpg.execution.connection.ConnectionProfile.MAX_DAMAGE_PULSES);
        assertThrows(IllegalArgumentException.class,()->new com.inigmasgames.hytalerpg.execution.connection.ConnectionProfile(p.kind(),p.range(),p.width(),p.height(),p.depth(),p.speed(),25.65,p.intervalSeconds(),p.coefficient(),p.upkeepPerSecond(),p.radius(),p.originHeight(),p.element(),p.details()));
    }
    @Test void all5742SkillPassiveCellsHaveExplicitVerdicts()throws Exception{
        var rows=new ArrayList<Map<String,Object>>();var profileFailures=new TreeMap<String,String>();var eligible=new TreeMap<String,Integer>();
        for(var skill:skills)for(var passive:passives){var r=assess(skill,List.of(passive),false);
            rows.add(Map.of("skill",skill.id().value(),"passive",passive.id().value(),"gate",r.gate,"detail",r.detail));
            if(r.gate.startsWith("COMPILED"))eligible.merge(passive.id().value(),1,Integer::sum);
            if(r.gate.equals("PROFILE_GATE"))profileFailures.put(skill.id()+"/"+passive.id(),r.detail);
        }
        write("skill-passive-matrix.json",rows);write("single-profile-gates.json",profileFailures);write("passive-implemented-profile-eligibility.json",eligible);
        assertEquals(5742,rows.size());assertEquals(66,eligible.size(),"Every primitive needs an implemented positive profile");
        assertTrue(profileFailures.isEmpty(),profileFailures.toString());
    }
    @Test void all2145PairsClassifiedAgainstAll87Skills()throws Exception{
        var rows=new ArrayList<Map<String,Object>>();var profileFailures=new TreeMap<String,String>();
        for(int a=0;a<passives.size();a++)for(int b=a+1;b<passives.size();b++){
            var selected=List.of(passives.get(a),passives.get(b));var accepted=new ArrayList<String>();var missing=new ArrayList<String>();var rejected=new TreeMap<String,Integer>();var gated=new TreeMap<String,String>();var capabilities=new TreeMap<String,String>();
            for(var skill:skills){var r=assess(skill,selected,false);
                switch(r.gate){case "COMPILED_PROFILE_RESOLVED_CONNECTED_UNVERIFIED"->accepted.add(skill.id().value());
                    case "COMPILED_PROFILE_WITH_EXPLICIT_RUNTIME_GATE"->capabilities.put(skill.id().value(),r.detail);
                    case "CATALOG_ELIGIBLE_RUNTIME_NOT_IMPLEMENTED"->missing.add(skill.id().value());
                    case "PROFILE_GATE"->{gated.put(skill.id().value(),r.detail);profileFailures.put(skill.id()+"/"+selected.getFirst().id()+"/"+selected.getLast().id(),r.detail);}
                    default->rejected.merge(r.detail,1,Integer::sum);}
            }
            rows.add(Map.of("first",selected.getFirst().id().value(),"second",selected.getLast().id().value(),"classification",accepted.isEmpty()?capabilities.isEmpty()?missing.isEmpty()?"NO_VALID_IMPLEMENTED_SKILL":"CATALOG_ONLY_PENDING_LEGACY_RUNTIME":"COMPILED_BUT_RUNTIME_CAPABILITY_GATED":"VALID_ON_LISTED_PROFILES",
                    "compiledProfileSkillsConnectedUnverified",accepted,"catalogOnlySkills",missing,"rejectedByCode",rejected,"profileGates",gated,"runtimeCapabilityGates",capabilities));
            assertEquals(87,accepted.size()+missing.size()+gated.size()+capabilities.size()+rejected.values().stream().mapToInt(Integer::intValue).sum());
        }
        write("passive-pair-matrix.json",rows);write("pair-profile-gates.json",profileFailures);
        assertEquals(2145,rows.size());assertTrue(profileFailures.isEmpty(),profileFailures.toString());
    }
    @Test void deterministicValidSixLinkGraphsPreserveOrderingScopeAndBudgets()throws Exception{
        var random=new Random(110033);int valid=0,attempts=0,rejected=0;var covered=new TreeSet<String>();
        for(;attempts<5000&&valid<1000;attempts++){
            var skill=skills.get(random.nextInt(skills.size()));if(!profiles.supports(skill.id().value()))continue;
            var shuffled=new ArrayList<>(passives);Collections.shuffle(shuffled,random);var selected=new ArrayList<PassiveDefinition>();
            for(var passive:shuffled){var next=new ArrayList<>(selected);next.add(passive);var r=assess(skill,next,false);if(r.gate.startsWith("COMPILED"))selected=next;else rejected++;if(selected.size()==6)break;}
            if(selected.size()!=6)continue;
            var direct=assess(skill,selected,false);var joints=assess(skill,selected,true);assertEquals(direct.gate,joints.gate);assertTrue(direct.gate.startsWith("COMPILED"));
            var reverse=new ArrayList<>(selected);Collections.reverse(reverse);var permuted=assess(skill,reverse,true);
            assertEquals(direct.plan.passiveOrder(),permuted.plan.passiveOrder());assertEquals(direct.plan.kernelModifiers(),permuted.plan.kernelModifiers());
            assertEquals(direct.plan.finalTags(),joints.plan.finalTags());assertEquals(direct.plan.powerModifiers(),joints.plan.powerModifiers());
            assertEquals(direct.plan.geometryModifiers(),joints.plan.geometryModifiers());assertEquals(6,direct.plan.passiveOrder().size());
            assertEquals(48,direct.plan.safetyBudgets().maxSpawnedEffects());assertEquals(16,direct.plan.safetyBudgets().maxTriggeredSecondaries());assertEquals(3,direct.plan.safetyBudgets().maxGeneration());
            assertTrue(Double.isFinite(direct.plan.kernelModifiers().resourceCostMultiplier()));assertTrue(direct.plan.kernelModifiers().resourceCostMultiplier()>0);
            covered.add(skill.id().value());valid++;
        }
        write("six-link-property-summary.json",Map.of("seed",110033,"validGraphs",valid,"attempts",attempts,"rejectedCandidateExtensions",rejected,"implementedProfilesCovered",covered,"connectedEvidence",false));
        assertEquals(1000,valid);assertTrue(covered.size()>=40,covered.toString());
    }
}
