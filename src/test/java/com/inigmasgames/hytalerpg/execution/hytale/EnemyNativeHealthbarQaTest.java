package com.inigmasgames.hytalerpg.execution.hytale;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EnemyNativeHealthbarQaTest {
    @Test void qaAttachmentAndCleanupPreserveEveryOtherUiComponent() {
        int[] original={2,5,9};
        int[] attached=EnemyHealthBarPresentation.withHealthbar(original,7);
        assertArrayEquals(new int[]{2,5,9,7},attached);
        assertArrayEquals(new int[]{2,5,9},original);
        assertArrayEquals(attached,EnemyHealthBarPresentation.withHealthbar(attached,7));
        assertArrayEquals(new int[]{2,5,9,11},
                EnemyHealthBarPresentation.withoutHealthbar(new int[]{2,5,9,7,11},7));
    }
}
