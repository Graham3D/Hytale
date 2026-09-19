package com.inigmasgames.persistentnpcs.perception;

import java.util.UUID;

public record PerceivedEntity(UUID entityId, String name, String kind, double distanceMeters) {
}
