package com.inigmasgames.hytalerpg.ui.character;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.inigmasgames.hytalerpg.combat.attribute.DerivedStats;
import com.inigmasgames.hytalerpg.combat.attribute.RpgAttribute;
import com.inigmasgames.hytalerpg.combat.hytale.DerivedStatEntityAdapter;
import com.inigmasgames.hytalerpg.phase00.BuildIdentity;
import com.inigmasgames.hytalerpg.progress.AttributeAllocationService;
import com.inigmasgames.hytalerpg.progress.MutationResult;
import com.inigmasgames.hytalerpg.ui.HytaleResourceViewAdapter;
import com.inigmasgames.hytalerpg.ui.RpgUiProjectionService;
import com.inigmasgames.hytalerpg.ui.model.CharacterSheetViewModel;
import com.inigmasgames.hytalerpg.ui.trace.RpgUiTraceService;

import javax.annotation.Nonnull;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Standalone server-authoritative Character page. */
public final class RpgCharacterPage extends InteractiveCustomUIPage<RpgCharacterPage.Data> {
    private final PlayerRef player;
    private final RpgUiProjectionService projection;
    private final AttributeAllocationService allocation;
    private final RpgUiTraceService trace;
    private final HytaleResourceViewAdapter resources = new HytaleResourceViewAdapter();
    private CharacterSheetViewModel model;

    public RpgCharacterPage(PlayerRef player, RpgUiProjectionService projection,
                            AttributeAllocationService allocation, RpgUiTraceService trace) {
        super(player, CustomPageLifetime.CanDismiss, Data.CODEC);
        this.player = player; this.projection = projection; this.allocation = allocation; this.trace = trace;
    }

    @Override public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder commands,
                                @Nonnull UIEventBuilder events, @Nonnull Store<EntityStore> store) {
        commands.append("RpgCharacter.ui");
        model = project(store, ref);
        render(commands, events, model, "");
        trace.trace(player.getUuid(), "CHARACTER_OPENED", id(), Map.of("revision", model.revision()));
    }

    @Override public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                                          @Nonnull Data data) {
        String correlation = id();
        RpgAttribute attribute;
        try { attribute = RpgAttribute.parse(data.attribute); }
        catch (RuntimeException error) { return; }
        long expectedRevision;
        try { expectedRevision = Long.parseLong(data.expectedRevision); }
        catch (NumberFormatException error) { return; }
        trace.trace(player.getUuid(), "ATTRIBUTE_ALLOCATE_REQUEST", correlation,
                Map.of("attribute", attribute.name(), "expectedRevision", expectedRevision));
        MutationResult result = allocation.allocate(player.getUuid(), attribute, expectedRevision, correlation);
        String status;
        if (result.success()) {
            CharacterSheetViewModel beforeApply = project(store, ref);
            EntityStatMap nativeStats = requireStats(store, ref);
            new DerivedStatEntityAdapter().apply(nativeStats, beforeApply.derivedStats());
            model = project(store, ref);
            status = "+1 " + attribute + " committed.";
            trace.trace(player.getUuid(), "ATTRIBUTE_ALLOCATE_COMMITTED", correlation,
                    Map.of("attribute", attribute.name(), "revision", result.revision(),
                            "raw", model.derivedStats().rawAttributes().get(attribute),
                            "unspent", model.unspentAttributePoints(), "pending", model.pendingLevelUpPoints()));
        } else {
            model = project(store, ref);
            status = result.code() + ": " + result.message();
            trace.trace(player.getUuid(), "ATTRIBUTE_ALLOCATE_REJECTED", correlation,
                    Map.of("attribute", attribute.name(), "code", result.code().name(),
                            "authoritativeRevision", result.revision()));
        }
        UICommandBuilder commands = new UICommandBuilder();
        UIEventBuilder events = new UIEventBuilder();
        render(commands, events, model, status);
        sendUpdate(commands, events, false);
        trace.trace(player.getUuid(), "CHARACTER_REFRESHED", correlation,
                Map.of("revision", model.revision(), "result", result.code().name()));
    }

    @Override public void onDismiss(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        trace.trace(player.getUuid(), "CHARACTER_CLOSED", id(), Map.of(
                "revision", model == null ? -1 : model.revision()));
    }

    private CharacterSheetViewModel project(Store<EntityStore> store, Ref<EntityStore> ref) {
        return projection.character(player.getUuid(), player.getUsername(), resources.read(requireStats(store, ref)));
    }

    private static EntityStatMap requireStats(Store<EntityStore> store, Ref<EntityStore> ref) {
        EntityStatMap stats = store.getComponent(ref, EntityStatMap.getComponentType());
        if (stats == null) throw new IllegalStateException("Hytale EntityStatMap is unavailable for this player.");
        return stats;
    }

    private static void render(UICommandBuilder commands, UIEventBuilder events,
                               CharacterSheetViewModel model, String status) {
        commands.set("#CharacterName.TextSpans", Message.raw(model.displayName()));
        commands.set("#Revision.TextSpans", Message.raw(BuildIdentity.REVISION + " / state " + model.revision()));
        commands.set("#LevelValue.TextSpans", Message.raw("Level " + model.xp().level()));
        String xp = model.xp().level() == 99 ? "XP CAP" : model.xp().xpIntoLevel() + " / " + model.xp().xpToNext();
        commands.set("#XpValue.TextSpans", Message.raw(xp + "  [" + pips(model) + "]"));
        commands.set("#PointsValue.TextSpans", Message.raw("Unspent: " + model.unspentAttributePoints()
                + "   Pending level-up: " + model.pendingLevelUpPoints()));
        commands.set("#Status.TextSpans", Message.raw(status));
        for (RpgAttribute attribute : RpgAttribute.values()) {
            String key = title(attribute);
            commands.set("#" + key + "Value.TextSpans", Message.raw("Raw "
                    + model.derivedStats().rawAttributes().get(attribute) + "   Effective "
                    + one(model.derivedStats().effective(attribute))));
            commands.set("#" + key + "Plus.Disabled", model.unspentAttributePoints() <= 0);
            events.addEventBinding(CustomUIEventBindingType.Activating, "#" + key + "Plus",
                    new EventData().append("Action", "allocate").append("Attribute", attribute.name())
                            .append("ExpectedRevision", Long.toString(model.revision())), false);
        }
        DerivedStats d = model.derivedStats();
        commands.set("#Pools.TextSpans", Message.raw("Max pools   Health " + one(d.maxHealth())
                + "   Mana " + one(d.maxMana()) + "   Stamina " + one(d.maxStamina())));
        commands.set("#Resources.TextSpans", Message.raw("Live native   Mana " + resource(model.mana())
                + " | Health " + resource(model.health()) + " | Stamina " + resource(model.stamina())));
        commands.set("#Damage.TextSpans", Message.raw("Damage   Heavy " + percent(d.heavyDamageMultiplier() - 1)
                + "   Light " + percent(d.lightDamageMultiplier() - 1)
                + "   Magic " + percent(d.magicDamageMultiplier() - 1)));
        commands.set("#Support.TextSpans", Message.raw("Healing " + percent(d.healingMultiplier() - 1)
                + "   Crit " + percent(d.criticalChance()) + " x" + one(d.criticalMultiplier())
                + "   CDR " + percent(d.cooldownRecovery())));
        commands.set("#Utility.TextSpans", Message.raw("Learn " + percent(d.learnRate())
                + "   Upgrade " + percent(d.upgradeSuccess()) + "   Magic Find " + percent(d.magicFind())));
    }

    private static String pips(CharacterSheetViewModel model) {
        StringBuilder result = new StringBuilder();
        for (double fill : model.xp().pipFill()) result.append(fill >= .999 ? '|' : fill <= .001 ? '.' : ':');
        return result.toString();
    }
    private static String title(RpgAttribute attribute) {
        String value = attribute.name().toLowerCase(Locale.ROOT);
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }
    private static String resource(com.inigmasgames.hytalerpg.ui.model.NativeResourceView value) {
        return one(value.current()) + '/' + one(value.maximum());
    }
    private static String percent(double value) { return one(value * 100.0) + '%'; }
    private static String one(double value) { return String.format(Locale.ROOT, "%.1f", value); }
    private static String id() { return UUID.randomUUID().toString().substring(0, 12); }

    public static final class Data {
        static final BuilderCodec<Data> CODEC = BuilderCodec.builder(Data.class, Data::new)
                .append(new KeyedCodec<>("Action", Codec.STRING), (d, v) -> d.action = v, d -> d.action).add()
                .append(new KeyedCodec<>("Attribute", Codec.STRING), (d, v) -> d.attribute = v, d -> d.attribute).add()
                .append(new KeyedCodec<>("ExpectedRevision", Codec.STRING),
                        (d, v) -> d.expectedRevision = v, d -> d.expectedRevision).add()
                .build();
        private String action = "";
        private String attribute = "";
        private String expectedRevision = "0";
    }
}
