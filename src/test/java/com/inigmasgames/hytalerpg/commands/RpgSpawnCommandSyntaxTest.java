package com.inigmasgames.hytalerpg.commands;

import com.inigmasgames.hytalerpg.enemies.EnemyQaSpawnRequest;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RpgSpawnCommandSyntaxTest {
    @Test void acceptsOwnerQaPositionalAffixes() {
        var aliases=QaSpawnAliasSyntax.positionalAliases(
                "rpg spawn Golem_Crystal_Earth unique hell stoneskin manaburn reflective",
                "Golem_Crystal_Earth","unique","hell");
        assertEquals(List.of("stoneskin","manaburn","reflective"),aliases);
        var request=EnemyQaSpawnRequest.parse("Golem_Crystal_Earth","unique","hell",aliases);
        request.validateExplicitCount(com.inigmasgames.hytalerpg.enemies.EnemyBalance.canonical());
        assertEquals(3,request.affixIds().size());
        assertEquals(List.of(),QaSpawnAliasSyntax.positionalAliases(
                "rpg spawn Larva_Void champion normal","Larva_Void","champion","normal"));
        var shortRequest=EnemyQaSpawnRequest.parse("Golem_Crystal_Earth","unique","hell",List.of("stoneskin"));
        assertEquals("Hell Unique requires 3 affixes; supplied 1",assertThrows(IllegalArgumentException.class,
                ()->shortRequest.validateExplicitCount(com.inigmasgames.hytalerpg.enemies.EnemyBalance.canonical())).getMessage());
    }

    @Test void rejectsNamedFlagInsteadOfIgnoringIt() {
        assertThrows(IllegalArgumentException.class,()->QaSpawnAliasSyntax.positionalAliases(
                "rpg spawn Larva_Void unique normal --affixAlias=stoneskin","Larva_Void","unique","normal"));
    }
}
