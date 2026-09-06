package com.inigmasgames.hytalerpg.combat.hytale;

import java.util.UUID;

public record HytaleDamageMetadata(UUID actorId, String rootCastId, String skillInstanceId,
                                   String correlationId, double preMitigationDamage, double targetHealthBefore) { }
