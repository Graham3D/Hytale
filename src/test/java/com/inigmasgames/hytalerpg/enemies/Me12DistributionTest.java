package com.inigmasgames.hytalerpg.enemies;

import com.google.gson.*;
import com.inigmasgames.hytalerpg.difficulty.DifficultyId;
import java.math.BigInteger;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Me12DistributionTest {
    static Map<String,String> documents(){var result=new LinkedHashMap<String,String>();Me12Policies.canonical().documents().forEach(d->result.put(d.reference().asset(),d.utf8()));return result;}
    static Me12Policies toy(){var docs=documents();docs.put("name-pools-v2.json","""
        {"schemaVersion":2,"revision":"ME12-test-toy","format":"{prefix} {suffix}",
        "prefixes":[{"id":"toy-prefix","text":"Toy","tier":2,"nameWeight":1,"signature":"ME-017","fillers":["ME-001","ME-002","ME-003","ME-023"]}],
        "suffixes":[{"id":"toy-suffix","text":"the Test","tier":2,"nameWeight":1,"signature":"ME-018","fillers":["ME-001","ME-002","ME-003","ME-023"]}]}
        """);return Me12Policies.load(docs);}
    static Me12Planner.Context withWeights(Me12Planner.Context c,Map<String,Long> weights){return new Me12Planner.Context(c.nativeSnapshotDigest(),c.sourceReceipt(),c.generation(),c.difficulty(),c.evidence(),c.leader(),c.minions(),c.hordeSlots(),weights);}
    @Test void S01S02DistinctSetsNotOwnershipArrangementsAndSelectorOracle() {
        var policy=toy();var c=Me12PlannerTest.context(DifficultyId.NORMAL);var weights=new TreeMap<>(c.effectiveWeights());weights.replaceAll((id,w)->0L);
        weights.put("ME-017",1L);weights.put("ME-018",1L);weights.put("ME-001",2L);weights.put("ME-002",3L);weights.put("ME-003",5L);
        for(boolean selectors:List.of(false,true)){
            if(selectors){weights.put("ME-023/MIGHT",7L);weights.put("ME-023/WARD",11L);weights.put("ME-023/HASTE",13L);}
            var compiled=Me12Planner.compile(withWeights(c,weights),policy,new Me12Planner.Budget(n->{}));
            var choices=new ArrayList<EnemyAffixSelection.Choice>();weights.forEach((key,w)->{if(w>0&&!Set.of("ME-017","ME-018").contains(key)){String[] parts=key.split("/");choices.add(new EnemyAffixSelection.Choice(parts[0],parts.length==1?null:EnemyAffixRegistry.Selector.valueOf(parts[1])));}});
            var selection=new EnemyAffixSelection(policy.registry());var oracle=new TreeMap<String,Long>();
            for(int i=0;i<choices.size();i++)for(int j=i+1;j<choices.size();j++){
                var set=List.of(new EnemyAffixSelection.Choice("ME-017",null),new EnemyAffixSelection.Choice("ME-018",null),choices.get(i),choices.get(j));
                if(selection.legal(new EnemyAffixSelection.Request(c.leader().binding(),DifficultyId.NORMAL,EnemyRarity.UNIQUE,4,List.of(),true),set))
                    oracle.put(choices.get(i).key()+"+"+choices.get(j).key(),weights.get(choices.get(i).key())*weights.get(choices.get(j).key()));
            }
            long total=oracle.values().stream().mapToLong(Long::longValue).sum();assertEquals(BigInteger.valueOf(total),compiled.completionMasses().get("toy-prefix/toy-suffix"));
            if(!selectors){assertEquals(Map.of("ME-001+ME-002",6L,"ME-001+ME-003",10L,"ME-002+ME-003",15L),oracle);assertEquals(31,total);}
            var observed=new TreeMap<String,Integer>();for(int i=0;i<100000;i++){
                var plan=compiled.sample("ME12-S02/fixed/"+i);assertTrue(compiled.accepts(plan));
                String key=plan.choices().stream().filter(ch->!Set.of("ME-017","ME-018").contains(ch.affixId())).map(EnemyAffixSelection.Choice::key).sorted().collect(java.util.stream.Collectors.joining("+"));observed.merge(key,1,Integer::sum);
            }
            for(var e:oracle.entrySet())assertCell(observed.getOrDefault(e.getKey(),0),100000,e.getValue()/(double)total,e.getKey());
        }
    }
    @Test void N03N06N07NameCountAndCompletionMassDoNotBiasTiersOrNameWeights() {
        var context=Me12PlannerTest.context(DifficultyId.HELL);var policy=Me12Policies.canonical();
        var original=Me12Planner.compile(context,policy,new Me12Planner.Budget(n->{}));
        assertTrue(original.completionMasses().values().stream().distinct().count()>1);
        assertTrue(original.nameMasses().values().stream().allMatch(w->w==1));
        assertFalse(original.nameMasses().containsKey("ashclaw/ashen"));assertFalse(original.nameMasses().containsKey("grimtooth/cruel"));
        var docs=documents();var names=JsonParser.parseString(docs.get("name-pools-v2.json")).getAsJsonObject();
        for(int i=0;i<100;i++){var extra=names.getAsJsonArray("prefixes").get(0).deepCopy().getAsJsonObject();extra.addProperty("id","extra_"+i);names.getAsJsonArray("prefixes").add(extra);}
        names.addProperty("revision","ME12-test-extra-names");docs.put("name-pools-v2.json",names.toString());
        var enlarged=Me12Planner.compile(context,Me12Policies.load(docs),new Me12Planner.Budget(n->{}));
        assertArrayEquals(original.tierMasses(),enlarged.tierMasses());assertEquals(original.states(),enlarged.states());
        var disabled=new TreeMap<>(context.effectiveWeights());disabled.put("ME-005",0L);
        var withoutFire=Me12Planner.compile(withWeights(context,disabled),policy,new Me12Planner.Budget(n->{}));
        assertTrue(withoutFire.nameMasses().keySet().stream().noneMatch(k->k.startsWith("ashclaw/")||k.endsWith("/ashen")));
    }
    @Test void N06FirstFeasibleNameInANewTierChangesOnlyItsIndicator(){
        var c=Me12PlannerTest.context(DifficultyId.NORMAL);var initial=toy();var before=Me12Planner.compile(c,initial,new Me12Planner.Budget(n->{}));
        assertArrayEquals(new long[]{0,0,0,0,625,0,0,0,0},before.tierMasses());
        var docs=new LinkedHashMap<String,String>();initial.documents().forEach(d->docs.put(d.reference().asset(),d.utf8()));
        var names=JsonParser.parseString(docs.get("name-pools-v2.json")).getAsJsonObject();
        names.getAsJsonArray("prefixes").add(new Gson().toJsonTree(new Me12Policies.Name("new-tier","New",1,1,"ME-017",Set.of())));
        names.addProperty("revision","ME12-first-feasibility");docs.put("name-pools-v2.json",names.toString());
        var after=Me12Planner.compile(c,Me12Policies.load(docs),new Me12Planner.Budget(n->{}));
        assertArrayEquals(new long[]{0,1750,0,0,625,0,0,0,0},after.tierMasses());
    }
    @Test void S05OneHundredThousandPinnedSamplesPerRepresentativeRoleAndEra() throws Exception {
        var policy=Me12Policies.canonical();var bindings=EnemyNativeBindings.load();var results=new ArrayList<Map<String,Object>>();
        for(String roleId:List.of("Trork_Warrior","Bear_Grizzly","Skeleton_Scout"))for(var era:DifficultyId.values()){
            var role=bindings.role(roleId).orElseThrow();
            var b=role.affixBinding(bindings.revision(),true,3);
            var member=new Me12Planner.Member(role.canonicalRoleId(),role.sourceValidationId(),b,role.nativeImmunityChannels(),true);
            var context=new Me12Planner.Context("a60fa0b31f1418691f8d8f7cc1159693e3ec504d4f0567fb91493a842d71f8ca",role.sourceValidationId(),1,era,Me12Planner.Evidence.CERTIFIED_STATIC_FIXTURE,member,List.of(member,member,member),List.of(member,member),Me12Planner.weights(policy.registry(),era,new EnemyAffixSelection.WeightPolicy(Map.of(),Set.of())));
            var compiled=Me12Planner.compile(context,policy,new Me12Planner.Budget(n->{}));long total=Arrays.stream(compiled.tierMasses()).sum();assertTrue(total>0,roleId+era);
            long[] observed=new long[9];long[] times=new long[100000];
            for(int i=0;i<100000;i++){
                String seed="ME12-S05/"+roleId+"/"+era+"/"+i;long before=System.nanoTime();var plan=compiled.sample(seed);times[i]=System.nanoTime()-before;
                assertTrue(compiled.accepts(plan),roleId+era+" illegal "+i);observed[(plan.prefix().tier()-1)*3+plan.suffix().tier()-1]++;
            }
            for(int cell=0;cell<9;cell++)assertCell(observed[cell],100000,compiled.tierMasses()[cell]/(double)total,roleId+"/"+era+"/"+cell);
            Arrays.sort(times);assertTrue(times[94999]<=500000,"p95 "+roleId+era+":"+times[94999]);assertTrue(times[98999]<=1000000,"p99 "+roleId+era+":"+times[98999]);
            results.add(Map.of("role",roleId,"era",era,"samples",100000,"tierMasses",compiled.tierMasses(),"observed",observed,"states",compiled.states(),"accountedGraphBytes",compiled.accountedBytes(),"coldNanos",compiled.compileNanos(),"warmP95Nanos",times[94999],"warmP99Nanos",times[98999]));
        }
        Path report=Path.of("build/reports/me12/stage2-distributions.json");Files.createDirectories(report.getParent());Files.writeString(report,new GsonBuilder().setPrettyPrinting().create().toJson(results));
    }
    // Declared before the run: seven binomial standard deviations plus seven counts, all 81 tier cells.
    // Zero-probability cells must be exactly zero. No seed retries or observed-data tolerance changes.
    static void assertCell(long count,int n,double p,String label){if(p==0){assertEquals(0,count,label);return;}assertTrue(Math.abs(count-n*p)<=7*Math.sqrt(n*p*(1-p))+7,label+": "+count+" expected "+n*p);}
}
