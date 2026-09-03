package com.inigmasgames.taverns;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;
import javax.annotation.Nonnull;

/** Identifies a server-authoritative Tavern patron across entity tracking changes. */
final class TavernPatronComponent implements Component<EntityStore> {
    static final BuilderCodec<TavernPatronComponent> CODEC = BuilderCodec
            .builder(TavernPatronComponent.class, TavernPatronComponent::new)
            .append(new KeyedCodec<>("TavernId", Codec.STRING),
                    (component, value) -> component.tavernId = value,
                    component -> component.tavernId)
            .add()
            .build();

    private String tavernId = new UUID(0L, 0L).toString();

    private TavernPatronComponent() {
    }

    TavernPatronComponent(UUID tavernId) {
        this.tavernId = tavernId.toString();
    }

    UUID tavernId() {
        return UUID.fromString(tavernId);
    }

    @Override
    @Nonnull
    public Component<EntityStore> clone() {
        return new TavernPatronComponent(tavernId());
    }
}
