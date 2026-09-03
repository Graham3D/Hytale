package com.inigmasgames.taverns;

import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.i18n.I18nModule;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import javax.annotation.Nonnull;

/** Persistent revision badge plus the per-Core Crystal Shard balance and resize delta. */
final class TavernsHud extends CustomUIHud {
    static final String KEY = "TavernsHud";

    enum CounterTone {
        WHITE,
        GREEN,
        RED
    }

    private boolean counterVisible;
    private String counterItemId;
    private int counterTotal;
    private int counterDelta;
    private CounterTone counterTone;
    private boolean comfortVisible;
    private int comfortValue;
    private List<ComfortSource> comfortSources = List.of();
    private boolean relaxedVisible;
    private boolean relaxingVisible;
    private int relaxingStep;
    private float serviceAnnouncementSeconds;
    private final PlayerRef playerRef;

    record ComfortSource(String assetId, int comfort) {
        ComfortSource {
            Objects.requireNonNull(assetId, "assetId");
        }
    }

    TavernsHud(PlayerRef playerRef) {
        super(playerRef, KEY, 100);
        this.playerRef = playerRef;
    }

    @Override
    protected void build(@Nonnull UICommandBuilder commandBuilder) {
        commandBuilder.append("Hud/TavernsRevision.ui");
        commandBuilder.set("#RevisionLabel.Text", "TAVERNS  " + TavernsPlugin.REVISION);
        populateCounter(commandBuilder);
        populateComfort(commandBuilder);
        populateComfortValueOverlay(commandBuilder);
        populateRelaxing(commandBuilder);
        populateServiceAnnouncement(commandBuilder, "");
    }

    void showShardBalance(String itemId, int currentTotal) {
        applyShardCounter(itemId, currentTotal, 0, CounterTone.WHITE, false);
    }

    void showShardBaseline(String itemId, int baselineTotal) {
        applyShardCounter(itemId, baselineTotal, 0, CounterTone.WHITE, true);
    }

    void updateShardCounter(String itemId, int currentTotal, int shardTransfer) {
        CounterTone tone = shardTransfer < 0
                ? CounterTone.GREEN
                : shardTransfer > 0 ? CounterTone.RED : CounterTone.WHITE;
        applyShardCounter(itemId, currentTotal, Math.abs(shardTransfer), tone, false);
    }

    private void applyShardCounter(String itemId, int currentTotal, int delta,
                                   CounterTone tone, boolean forceUpdate) {
        if (!forceUpdate && counterVisible
                && itemId.equals(counterItemId)
                && currentTotal == counterTotal
                && delta == counterDelta
                && tone == counterTone) {
            return;
        }
        counterVisible = true;
        counterItemId = itemId;
        counterTotal = currentTotal;
        counterDelta = delta;
        counterTone = tone;
        UICommandBuilder commands = new UICommandBuilder();
        populateCounter(commands);
        update(false, commands);
    }

    void hideShardCounter() {
        if (!counterVisible) {
            return;
        }
        counterVisible = false;
        counterDelta = 0;
        UICommandBuilder commands = new UICommandBuilder();
        commands.set("#ShardCounter.Visible", false);
        update(false, commands);
    }

    private void populateCounter(UICommandBuilder commands) {
        commands.set("#ShardCounter.Visible", counterVisible);
        if (!counterVisible || counterItemId == null || counterTone == null) {
            return;
        }
        String total = Integer.toString(counterTotal);
        boolean hasDelta = counterDelta > 0 && counterTone != CounterTone.WHITE;
        commands.set("#ShardIcon.ItemStacks", new ItemStack[]{new ItemStack(counterItemId)});
        commands.set("#ShardCountWhite.Text", total);
        commands.set("#ShardTransactionWhite.Text", total);
        commands.set("#ShardCountGreen.Text", "(+" + counterDelta + ")");
        commands.set("#ShardCountRed.Text", "(-" + counterDelta + ")");
        commands.set("#ShardBalanceOnly.Visible", !hasDelta);
        commands.set("#ShardTransaction.Visible", hasDelta);
        commands.set("#ShardCountGreen.Visible", hasDelta && counterTone == CounterTone.GREEN);
        commands.set("#ShardCountRed.Visible", hasDelta && counterTone == CounterTone.RED);
    }

    void showComfort(int value, List<ComfortSource> sources) {
        List<ComfortSource> copiedSources = List.copyOf(sources);
        if (comfortVisible && comfortValue == value && comfortSources.equals(copiedSources)) {
            return;
        }
        comfortVisible = true;
        comfortValue = value;
        comfortSources = copiedSources;
        UICommandBuilder commands = new UICommandBuilder();
        populateComfort(commands);
        populateComfortValueOverlay(commands);
        update(false, commands);
    }

    void hideComfort() {
        if (!comfortVisible) {
            return;
        }
        comfortVisible = false;
        comfortSources = List.of();
        UICommandBuilder commands = new UICommandBuilder();
        populateComfort(commands);
        populateComfortValueOverlay(commands);
        update(false, commands);
    }

    private void populateComfort(UICommandBuilder commands) {
        commands.set("#ComfortPanel.Visible", comfortVisible);
        if (comfortVisible) {
            commands.set("#ComfortTotalLabel.Text", "Comfort: " + comfortValue);
            commands.set(
                    "#ComfortSourcesLabel.Text",
                    formatComfortSources(comfortSources, this::localizedAssetName));
        }
    }

    void setRelaxedVisible(boolean visible) {
        if (relaxedVisible == visible) {
            return;
        }
        relaxedVisible = visible;
        UICommandBuilder commands = new UICommandBuilder();
        populateComfortValueOverlay(commands);
        update(false, commands);
    }

    private void populateComfortValueOverlay(UICommandBuilder commands) {
        commands.set("#ComfortValueOverlay.Visible", comfortVisible);
        commands.set("#ComfortValueWithRelaxed.Visible", comfortVisible && relaxedVisible);
        commands.set("#ComfortValueOnly.Visible", comfortVisible && !relaxedVisible);
        if (comfortVisible) {
            String value = Integer.toString(comfortValue);
            commands.set("#ComfortValueWithRelaxed.Text", value);
            commands.set("#ComfortValueOnly.Text", value);
        }
    }

    void showServiceAnnouncement(String text) {
        serviceAnnouncementSeconds = 3.0f;
        UICommandBuilder commands = new UICommandBuilder();
        populateServiceAnnouncement(commands, text);
        update(false, commands);
    }

    void tickServiceAnnouncement(float delta) {
        if (serviceAnnouncementSeconds <= 0.0f) {
            return;
        }
        serviceAnnouncementSeconds = Math.max(0.0f, serviceAnnouncementSeconds - delta);
        if (serviceAnnouncementSeconds == 0.0f) {
            UICommandBuilder commands = new UICommandBuilder();
            commands.set("#ServiceAnnouncement.Visible", false);
            update(false, commands);
        }
    }

    private void populateServiceAnnouncement(UICommandBuilder commands, String text) {
        boolean visible = serviceAnnouncementSeconds > 0.0f;
        commands.set("#ServiceAnnouncement.Visible", visible);
        if (visible) {
            commands.set("#ServiceAnnouncementLabel.Text", text);
        }
    }

    void showRelaxing(float progress) {
        int step = Math.clamp((int) Math.floor(progress * 20.0f), 0, 20);
        if (relaxingVisible && relaxingStep == step) {
            return;
        }
        relaxingVisible = true;
        relaxingStep = step;
        UICommandBuilder commands = new UICommandBuilder();
        populateRelaxing(commands);
        update(false, commands);
    }

    void hideRelaxing() {
        if (!relaxingVisible) {
            return;
        }
        relaxingVisible = false;
        relaxingStep = 0;
        UICommandBuilder commands = new UICommandBuilder();
        commands.set("#RelaxingHud.Visible", false);
        update(false, commands);
    }

    private void populateRelaxing(UICommandBuilder commands) {
        commands.set("#RelaxingHud.Visible", relaxingVisible);
        for (int index = 1; index <= 20; index++) {
            commands.set("#RelaxingFill" + index + ".Visible",
                    relaxingVisible && index <= relaxingStep);
        }
    }

    static String formatComfortSources(
            List<ComfortSource> sources,
            Function<String, String> nameResolver) {
        if (sources.isEmpty()) {
            return "No contributing assets";
        }
        StringBuilder text = new StringBuilder();
        for (int index = 0; index < sources.size(); index++) {
            ComfortSource source = sources.get(index);
            if (index > 0) {
                text.append('\n');
            }
            text.append(nameResolver.apply(source.assetId()))
                    .append("  +")
                    .append(source.comfort());
        }
        return text.toString();
    }

    private String localizedAssetName(String assetId) {
        Item item = Item.getAssetMap().getAsset(assetId);
        if (item != null) {
            String language = playerRef.getLanguage();
            if (language == null || language.isBlank()) {
                language = I18nModule.DEFAULT_LANGUAGE;
            }
            String localized = I18nModule.get().getMessage(language, item.getTranslationKey());
            if (localized != null && !localized.isBlank()) {
                return localized;
            }
        }
        return humanizeAssetId(assetId);
    }

    private static String humanizeAssetId(String assetId) {
        String[] words = assetId.replace('-', '_').split("_+");
        StringBuilder name = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            if (!name.isEmpty()) {
                name.append(' ');
            }
            name.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) {
                name.append(word.substring(1));
            }
        }
        return name.isEmpty() ? assetId : name.toString();
    }
}
