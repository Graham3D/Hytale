package com.inigmasgames.canvasui.runtime;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SessionRegistryTest {
    @Test void simultaneousPlayersRemainIsolated() {
        SessionRegistry<Object> registry = new SessionRegistry<>();
        UUID first = UUID.randomUUID(); UUID second = UUID.randomUUID();
        Object a = new Object(); Object b = new Object();
        registry.register(first, a); registry.register(second, b);
        assertSame(a, registry.get(first)); assertSame(b, registry.get(second)); assertEquals(2, registry.size());
    }

    @Test void cleanupIsIdempotentAndIdentitySafe() {
        SessionRegistry<Object> registry = new SessionRegistry<>();
        UUID id = UUID.randomUUID(); Object session = new Object();
        registry.register(id, session);
        registry.remove(id, new Object());
        assertSame(session, registry.get(id));
        registry.remove(id, session); registry.remove(id, session); registry.clear(); registry.clear();
        assertEquals(0, registry.size());
    }

    @Test void duplicatePlayerSessionIsRejected() {
        SessionRegistry<Object> registry = new SessionRegistry<>(); UUID id = UUID.randomUUID();
        registry.register(id, new Object());
        assertThrows(IllegalStateException.class, () -> registry.register(id, new Object()));
    }
}
