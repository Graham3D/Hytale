import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemAbility;
import org.bson.BsonDocument;
import java.nio.file.*;

/** Source launcher: native discriminator reproduction only; never starts a server. */
class U7P5AbilityDecodeProbe {
    public static void main(String[] args) throws Exception {
        var document=BsonDocument.parse(Files.readString(Path.of(args[0])));
        try {
            ItemAbility.CODEC.decode(document.getDocument("Ability"),new ExtraInfo());
            throw new AssertionError("Expected the legacy U7P4 discriminator to fail on U7P5");
        } catch(com.hypixel.hytale.codec.lookup.ACodecMapCodec.UnknownIdException expected) {
            System.out.println("REPRODUCED with installed U7P5 codec: "+expected.getMessage());
        }
    }
}
