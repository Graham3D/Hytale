package com.inigmasgames.hytalerpg;

import com.google.gson.GsonBuilder;
import com.inigmasgames.hytalerpg.content.RpgCatalog;
import com.inigmasgames.hytalerpg.execution.Stage04SkillProfiles;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** A coverage inventory is NOT an execution certificate. Explicit incompleteness blocks RC. */
class Stage13CoverageLedgerTest {
    @Test void everyCanonicalRecordHasAnExplicitRuntimeAndEvidenceDisposition()throws Exception {
        var catalog=RpgCatalog.loadCanonical();var profiles=Stage04SkillProfiles.loadCanonical(catalog);
        var skills=new ArrayList<Map<String,Object>>();var passives=new ArrayList<Map<String,Object>>();
        var gates=new TreeMap<String,String>();var rows=new StringBuilder("# Stage 13 coverage inventory\n\nGenerated from the canonical catalog and resolved runtime registry. Not a connected execution certificate. The native integration exceptions below are unresolved implementations, not proof that Hytale can never support them. Stage 13 is not closed.\n\n## Skills\n\n| Skill | Family | Disposition | Limitation |\n|---|---|---|---|\n");
        for(var skill:catalog.skills().stream().sorted(Comparator.comparing(s->s.id().value())).toList()){
            var p=profiles.require(skill.id().value());String gate=p.activationGate();
            if(!gate.isEmpty())gates.put(skill.id().value(),gate);
            String status=gate.isEmpty()?"IMPLEMENTED_AWAITING_CONNECTED_VERIFICATION":
                p.cage()!=null?"INTENTIONALLY_DISABLED_BY_MASTER_COLLISION_GATE":"IMPLEMENTATION_BLOCKED_NATIVE_INTEGRATION_UNVERIFIED";
            var limitations=new ArrayList<String>();if(!gate.isEmpty())limitations.add(gate);
            if(skill.id().value().equals("flame_weapon"))limitations.add("Native basic-hit application is not integrated; supported RPG hit path only");
            if(skill.id().value().equals("pedanticism"))limitations.add("Native enemy cooldown-progress adapter unavailable; RPG authority only");
            if(skill.id().value().equals("revive_fallen")||skill.id().value().equals("dominate"))limitations.add("Native source-role coverage restricted to audited Wolf_Black");
            limitations.add("Connected input, execution, visual/animation and multiplayer QA UNVERIFIED");
            skills.add(Map.of("id",skill.id().value(),"name",skill.name(),"family",p.family(),"status",status,"activationGate",gate,"limitations",limitations,"connectedProof",false));
            rows.append("| ").append(skill.name()).append(" (`").append(skill.id().value()).append("`) | ").append(p.family()).append(" | ").append(status).append(" | ").append(String.join("; ",limitations)).append(" |\n");
        }
        assertEquals(87,skills.size());assertEquals(87,profiles.all().size());assertEquals(3,gates.size());
        assertTrue(gates.containsKey("frenzy"));assertTrue(gates.containsKey("guard"));assertTrue(gates.containsKey("bone_cage"));
        assertFalse(gates.containsKey("snipe")); // T owner-authored range; connected release still unverified.
        rows.append("\n## Passives\n\nEvery row has retained compiler/runtime tests; exact eligible/rejected targets are in the 5,742-cell matrix. Native/client behavior is still unverified.\n\n| Passive | Modifier operations | Status |\n|---|---|---|\n");
        for(var p:catalog.passives().stream().sorted(Comparator.comparing(v->v.id().value())).toList()){
            passives.add(Map.of("id",p.id().value(),"name",p.name(),"modifierOps",p.modifierOps(),"phase",p.phase(),"status","IMPLEMENTED_AWAITING_CONNECTED_VERIFICATION","connectedProof",false,
                "limitations",List.of("Only compiler-approved targets; see exact matrix and runtime tests","Recipient skill native/role/API limitations also apply","No positive connected all-passive execution evidence")));
            rows.append("| ").append(p.name()).append(" (`").append(p.id().value()).append("`) | ").append(p.modifierOps().toString().replace("|","\\|")).append(" | IMPLEMENTED_AWAITING_CONNECTED_VERIFICATION |\n");
        }
        assertEquals(66,passives.size());assertTrue(passives.stream().noneMatch(p->p.get("name").equals("Swift Recovery")));
        Path output=Path.of("build/stage13-hardening");Files.createDirectories(output);
        Files.writeString(output.resolve("coverage.json"),new GsonBuilder().setPrettyPrinting().create().toJson(Map.of("skills",skills,"passives",passives,"runtimeGates",gates,
            "releaseReady",false,"connectedProof",false,"classificationCompleteForRelease",false,"explanation","Three native integration gaps are not established universal engine impossibilities; do not promote them to implemented/proven-capability-blocked.")));
        Files.writeString(output.resolve("coverage.md"),rows);
    }
}
