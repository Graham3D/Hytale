package com.inigmasgames.hytalerpg.execution.hytale;
/** Durable root retained; a native actor unloaded before attachment could finish. LOAD replay owns continuation. */
final class NativeBirthAwaitingLoad extends IllegalStateException {
    NativeBirthAwaitingLoad(){super("ENEMY_BIRTH_AWAITING_NATIVE_LOAD");}
}
