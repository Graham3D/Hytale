package com.inigmasgames.hytalerpg.enemies;

import com.inigmasgames.hytalerpg.difficulty.DifficultyId;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EnemyBalanceTest {
    @Test void canonicalValuesAndExportsAreFrozen(){
        var balance=EnemyBalance.canonical();assertEquals(12,balance.promotion().worldLimit());assertEquals(3,balance.promotion().cellLimit());
        assertEquals(2,balance.immunityCap(EnemyRarity.UNIQUE,DifficultyId.HELL));
        var json=balance.export();json.getAsJsonObject("aggregateCaps").addProperty("allDirectIncrease",.1);
        assertEquals(.6,balance.caps().allDirectIncrease());assertEquals(.1,new EnemyBalance(json).caps().allDirectIncrease());
    }
    @Test void invalidNestedPolicyNeverReplacesValidRegistry(){
        var valid=EnemyBalance.canonical();
        for(String section:new String[]{"loot","learning","promotion","immunity","qa","presentation","aggregateCaps"}){
            var document=valid.export();document.getAsJsonObject(section).addProperty("unknown",true);
            assertThrows(IllegalArgumentException.class,()->new EnemyBalance(document));
        }
        var document=valid.export();document.getAsJsonObject("qa").addProperty("qaEconomicRewards",true);
        assertThrows(IllegalArgumentException.class,()->new EnemyBalance(document));
        for(String key:new String[]{"requiresCopiedSaveGuard","requiresActiveRpgSave"}){
            var wrongSave=valid.export();wrongSave.getAsJsonObject("qa").addProperty(key,key.equals("requiresCopiedSaveGuard"));
            assertThrows(IllegalArgumentException.class,()->new EnemyBalance(wrongSave));
        }
        var bad=valid.export();bad.getAsJsonObject("promotion").getAsJsonObject("weightsByDifficulty")
                .getAsJsonObject("HELL").addProperty("UNIQUE","160");
        assertThrows(IllegalArgumentException.class,()->new EnemyBalance(bad));
        var wrongSplit=valid.export();wrongSplit.getAsJsonObject("promotion").getAsJsonObject("weightsByDifficulty")
                .getAsJsonObject("NORMAL").addProperty("CHAMPION",20);
        wrongSplit.getAsJsonObject("promotion").getAsJsonObject("weightsByDifficulty")
                .getAsJsonObject("NORMAL").addProperty("UNIQUE",61);
        assertThrows(IllegalArgumentException.class,()->new EnemyBalance(wrongSplit));
        assertEquals(.6,valid.caps().allDirectIncrease());
    }
}
