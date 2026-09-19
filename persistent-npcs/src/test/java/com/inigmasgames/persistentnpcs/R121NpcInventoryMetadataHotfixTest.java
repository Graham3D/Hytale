package com.inigmasgames.persistentnpcs;

import com.inigmasgames.persistentnpcs.profile.NpcInventoryState;
import java.lang.reflect.Method;
import org.bson.BsonDocument;

/** Regression gate for client-fatal empty persisted ItemStack metadata. */
public final class R121NpcInventoryMetadataHotfixTest {
    private R121NpcInventoryMetadataHotfixTest() { }

    public static void main(String[] arguments) throws Exception {
        Method decode = NpcInventoryState.PersistedItemStack.class
                .getDeclaredMethod("decodeMetadata", String.class);
        decode.setAccessible(true);
        assert decode.invoke(null, "{}") == null
                : "Legacy {} must not become a client-invalid metadata object";
        assert decode.invoke(null, "") == null;
        assert decode.invoke(null, new Object[] { null }) == null;

        BsonDocument metadata = (BsonDocument) decode.invoke(
                null, "{\"custom\": \"kept\"}");
        assert metadata != null && metadata.getString("custom").getValue().equals("kept")
                : "Non-empty authoritative metadata must remain lossless";

        System.out.println("R121 NPC inventory metadata hotfix gate passed.");
    }
}
