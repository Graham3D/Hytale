package checkpointc.fixture;

import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import javax.annotation.Nonnull;

/** Loader-only fixture: same plugin identity, deliberately no bridge-v1 contract. */
public final class IncompatibleImmersivePlugin extends JavaPlugin {
    public IncompatibleImmersivePlugin(@Nonnull JavaPluginInit init) { super(init); }
    @Override protected void setup() {
        getLogger().atInfo().log("CHECKPOINT_C_INCOMPATIBLE_FIXTURE setup=PASS");
    }
    @Override protected void start() {
        getLogger().atInfo().log("CHECKPOINT_C_INCOMPATIBLE_FIXTURE start=PASS");
    }
}
