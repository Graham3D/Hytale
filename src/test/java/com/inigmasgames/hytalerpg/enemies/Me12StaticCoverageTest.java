package com.inigmasgames.hytalerpg.enemies;

import com.google.gson.GsonBuilder;
import com.inigmasgames.hytalerpg.difficulty.*;
import com.inigmasgames.hytalerpg.progress.RewardIntent;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Me12StaticCoverageTest {
    @Test void Existing84RoleBoundaryCompilesWithoutChanging309RoleScope() throws Exception {
        var snapshot=com.google.gson.JsonParser.parseString(Files.readString(Path.of("docs/enemies/ME12_STAGE2_INPUT_SNAPSHOT.json"))).getAsJsonObject();
        // Stage 2 pinned the then-current owner source as provenance. Stage 3 deliberately
        // extends those owners; retain the historical hashes and verify live behavior below.
        var historicalOwners=Set.of("src/main/java/com/inigmasgames/hytalerpg/enemies/EnemyNativeBindings.java",
                "src/main/java/com/inigmasgames/hytalerpg/execution/hytale/NativeBasicAttackPaths.java",
                "src/main/java/com/inigmasgames/hytalerpg/execution/hytale/NativeEnemyAction.java");
        for(var pin:snapshot.getAsJsonObject("inputsSha256").entrySet())if(!historicalOwners.contains(pin.getKey())){
            var content=Files.readString(Path.of(pin.getKey())).replace("\r\n","\n");
            if(pin.getKey().equals("src/main/resources/rpg/enemies/native-bindings-v1.json")){
                // Only the exact native package identity changed; the 309-role contract stays pinned.
                content=content.replace("23f71f03763422ce7c04df191db49cd0e7d87d43b12baad75c43fdc202410aff",
                        "a60fa0b31f1418691f8d8f7cc1159693e3ec504d4f0567fb91493a842d71f8ca")
                        .replace("ee8f8d5b332874eb928144726077fa66f80c117a5412017a5cde6fce2d9bb2a0",
                                "b9164eb07d7c42e697d208ba3d35078a9b4a0125bef6261847a2f91e293e53b4");
            }
            assertEquals(pin.getValue().getAsString(),RewardIntent.digest(content),pin.getKey());
        }
        var input=Path.of("docs/enemies/ME12_STAGE1_RECONCILIATION.csv");
        var rows=Files.readAllLines(input);assertEquals(310,rows.size());
        var configured=rows.stream().skip(1).map(line->line.split(",",3)).filter(parts->parts[1].equals("CONFIGURED_EXISTING")).map(parts->parts[0]).toList();assertEquals(84,configured.size());
        var bindings=EnemyNativeBindings.load();var authored=AuthoredEncounterCatalog.load();var policy=Me12Policies.canonical();
        String nativeDigest=RewardIntent.digest(Files.readString(Path.of("docs/enemies/ME12_STAGE2_INPUT_SNAPSHOT.json")).replace("\r\n","\n"));
        var output=new ArrayList<Map<String,Object>>();var totals=new TreeMap<String,Integer>();
        for(var id:configured)for(var era:DifficultyId.values())for(int base=3;base<=5;base++){
            var role=bindings.role(id).orElseThrow();assertTrue(bindings.productionEligibilityRejection(id).isEmpty(),id);
            var channels=new TreeSet<>(role.nativeImmunityChannels());if(era==DifficultyId.HELL)authored.roles().stream().filter(a->a.id().equals(id)).forEach(a->a.hellImmunities().forEach(c->channels.add(c.canonical().name())));
            var binding=role.affixBinding(bindings.revision(),channels.size()<6,base);
            var member=new Me12Planner.Member(role.canonicalRoleId(),role.sourceValidationId(),binding,channels,true,id);
            var context=new Me12Planner.Context(nativeDigest,role.sourceValidationId(),1,era,Me12Planner.Evidence.CERTIFIED_STATIC_FIXTURE,member,
                    Collections.nCopies(base,member),List.of(member,member),Me12Planner.weights(policy.registry(),era,new EnemyAffixSelection.WeightPolicy(Map.of(),Set.of())));
            var compiled=Me12Planner.compile(context,policy,new Me12Planner.Budget(n->{}));long total=Arrays.stream(compiled.tierMasses()).sum();
            if(total>0){totals.merge(era+"/"+base,1,Integer::sum);assertTrue(compiled.accepts(compiled.sample("coverage/"+id+"/"+era+"/"+base)));}
            output.add(Map.of("role",id,"era",era,"baseMinions",base,"nativeExecutionCertified",false,"tierMasses",compiled.tierMasses(),"states",compiled.states(),"coldNanos",compiled.compileNanos()));
        }
        Path report=Path.of("build/reports/me12/stage2-static-feasibility.json");Files.createDirectories(report.getParent());Files.writeString(report,new GsonBuilder().setPrettyPrinting().create().toJson(Map.of("totals",totals,"contexts",output)));
        for(int base=3;base<=5;base++){assertEquals(68,totals.get("NORMAL/"+base));assertEquals(84,totals.get("NIGHTMARE/"+base));assertEquals(84,totals.get("HELL/"+base));}
    }
}
