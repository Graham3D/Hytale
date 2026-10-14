package com.inigmasgames.hytalerpg.execution.hytale;

import org.junit.jupiter.api.Test;

/** Exercises uninterrupted natural publication in a separate native ECS process. */
class NativeEnemyBirthPublicationIntegrationTest extends NativeEnemyBirthOwnerFixture {
    @Test void naturalVoidSpawnPublishesDurableV2Birth() throws Exception {
        exercise(false,"Larva_Void");
    }
}
