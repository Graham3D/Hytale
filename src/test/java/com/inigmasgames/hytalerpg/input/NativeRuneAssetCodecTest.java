package com.inigmasgames.hytalerpg.input;

import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.protocol.AbilitySlot;
import com.hypixel.hytale.server.core.asset.type.item.config.CoreItemAbility;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemAbility;
import java.nio.file.*;
import org.bson.BsonDocument;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeRuneAssetCodecTest {
    @Test void everyRpgRuneUsesTheInstalledNativeAbilityCodec() throws Exception {
        var directory=Path.of("src/main/resources/Server/Item/Items/RPG/Abilities");
        try(var entries=Files.list(directory)){
            var files=entries.filter(path->path.toString().endsWith(".json")).sorted().toList();
            assertEquals(97,files.size());
            for(var file:files){
                var item=BsonDocument.parse(Files.readString(file));
                assertTrue(item.containsKey("Ability"),file.toString());
                var abilityDocument=item.getDocument("Ability").clone();
                assertFalse(abilityDocument.containsKey("Tags"),file.toString());
                // Cast uses an AssetStore-only contained codec; inspect its unchanged root separately.
                var cast=abilityDocument.remove("Cast").asString().getValue();
                var ability=assertInstanceOf(CoreItemAbility.class,
                        ItemAbility.CODEC.decode(abilityDocument,new ExtraInfo()),file.toString());
                assertEquals(AbilitySlot.Core,ability.getSlot(),file.toString());
                assertEquals(0,ability.getCost(),file.toString());
                assertEquals(0,ability.getCooldownS(),file.toString());
                var itemId=file.getFileName().toString().replaceFirst("\\.json$","");
                assertEquals(NativeAbilityBridgeAudit.rootForItem(itemId),cast,file.toString());
            }
        }
    }
}
