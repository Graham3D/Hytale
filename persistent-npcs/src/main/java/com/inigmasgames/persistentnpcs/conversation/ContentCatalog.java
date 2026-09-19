package com.inigmasgames.persistentnpcs.conversation;

import com.inigmasgames.persistentnpcs.perception.NpcPerceptionSnapshot;

@FunctionalInterface
public interface ContentCatalog {
    ContentValidationResult validate(
            String requestedThing, NpcPerceptionSnapshot perception);

    static ContentCatalog unavailable() {
        return (requestedThing, perception) -> ContentValidationResult.unknown(
                requestedThing, "Hytale content registry is unavailable");
    }
}
