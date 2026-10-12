package com.inigmasgames.hytalerpg.execution.hytale;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class Chapter1InstanceAdmissionTest {
    @Test void initialAdmissionAcceptsTheInstancedWorldButReadyRearmRemainsExact() {
        UUID connectWorld=UUID.randomUUID();
        UUID instanceWorld=UUID.randomUUID();
        assertTrue(HytalePlayerPersistenceReady.acceptsReadyWorld(null,instanceWorld),
                "the first owner tick may run in an instance created after PlayerConnectEvent");
        assertFalse(HytalePlayerPersistenceReady.acceptsReadyWorld(connectWorld,instanceWorld));
        assertTrue(HytalePlayerPersistenceReady.acceptsReadyWorld(instanceWorld,instanceWorld));
        assertFalse(HytalePlayerPersistenceReady.acceptsReadyWorld(instanceWorld,connectWorld),
                "a later PlayerReadyEvent must still reject a stale world");
        assertFalse(HytalePlayerPersistenceReady.acceptsReadyWorld(null,null));
    }
}
