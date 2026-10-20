import java.nio.file.*;
import org.bson.BsonDocument;
import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.server.core.asset.type.particle.config.ParticleSpawner;
import com.hypixel.hytale.server.core.asset.type.particle.config.ParticleSystem;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
public class CycloneCodecProbe {
  public static void main(String[] args) throws Exception {
    try { run(); Files.writeString(Path.of("build/cyclone-codec-result.txt"),"PASS"); }
    catch(Throwable error) { var out=new java.io.StringWriter(); error.printStackTrace(new java.io.PrintWriter(out)); Files.writeString(Path.of("build/cyclone-codec-result.txt"),out.toString()); throw error; }
  }
  static void run() throws Exception {
    Path root=Path.of("src/main/resources/Server");
    var info=new ExtraInfo();
    
    
    var effect=EntityEffect.CODEC.decode(BsonDocument.parse(Files.readString(root.resolve("Entity/Effects/RPG/RPG_Cyclone_Armor.json"))),info);
    if(effect==null)throw new IllegalStateException("missing decoded asset");
  }
}


