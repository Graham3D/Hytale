package com.inigmasgames.persistentnpcs.llm;

import com.google.gson.JsonObject;
import java.util.UUID;

/** Optional observable runtime metrics exposed by a routed LLM provider. */
public interface LlmRuntimeDiagnosticSource {
    JsonObject runtimeDiagnostics(UUID npcId);
}
