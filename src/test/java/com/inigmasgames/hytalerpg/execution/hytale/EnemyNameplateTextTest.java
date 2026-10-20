package com.inigmasgames.hytalerpg.execution.hytale;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class EnemyNameplateTextTest {
    @Test void namePrecedesFrozenLevelAndUnknownLevelIsOmitted(){
        assertEquals("Skeleton Archer  Lv. 18",EnemyNameplateText.format(" Skeleton Archer ",18));
        assertEquals("Skeleton Archer",EnemyNameplateText.format("Skeleton Archer",0));
        assertThrows(IllegalArgumentException.class,()->EnemyNameplateText.format(" ",18));
    }
}
