package com.inigmasgames.persistentnpcs.memory;

import java.util.Optional;

/** Optional local semantic-memory extension; structured/lexical retrieval remains available. */
public interface EmbeddingProvider {
    Optional<float[]> embed(String text);

    default boolean available() {
        return false;
    }
}
