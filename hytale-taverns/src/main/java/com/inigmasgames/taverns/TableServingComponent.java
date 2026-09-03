package com.inigmasgames.taverns;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;

/** Durable state for one visual part of a Table serving. */
final class TableServingComponent implements Component<EntityStore> {
    static final BuilderCodec<TableServingComponent> CODEC = BuilderCodec
            .builder(TableServingComponent.class, TableServingComponent::new)
            .append(new KeyedCodec<>("TableX", Codec.INTEGER),
                    (component, value) -> component.tableX = value,
                    component -> component.tableX)
            .add()
            .append(new KeyedCodec<>("TableY", Codec.INTEGER),
                    (component, value) -> component.tableY = value,
                    component -> component.tableY)
            .add()
            .append(new KeyedCodec<>("TableZ", Codec.INTEGER),
                    (component, value) -> component.tableZ = value,
                    component -> component.tableZ)
            .add()
            .append(new KeyedCodec<>("Part", Codec.STRING),
                    (component, value) -> component.part = value,
                    component -> component.part)
            .add()
            .append(new KeyedCodec<>("Item", ItemStack.CODEC),
                    (component, value) -> component.itemStack = value,
                    component -> component.itemStack)
            .add()
            .build();

    private int tableX;
    private int tableY;
    private int tableZ;
    private String part = Part.FOOD.serializedName;
    private ItemStack itemStack;

    private TableServingComponent() {
    }

    TableServingComponent(int tableX, int tableY, int tableZ) {
        this(tableX, tableY, tableZ, Part.FOOD, null);
    }

    TableServingComponent(
            int tableX,
            int tableY,
            int tableZ,
            Part part,
            ItemStack itemStack) {
        this.tableX = tableX;
        this.tableY = tableY;
        this.tableZ = tableZ;
        this.part = part.serializedName;
        this.itemStack = itemStack;
    }

    int tableX() {
        return tableX;
    }

    int tableY() {
        return tableY;
    }

    int tableZ() {
        return tableZ;
    }

    Part part() {
        return Part.fromSerialized(part);
    }

    ItemStack itemStack() {
        return itemStack;
    }

    boolean isLegacyDroppedItem() {
        return itemStack == null;
    }

    @Override
    @Nonnull
    public Component<EntityStore> clone() {
        return new TableServingComponent(tableX, tableY, tableZ, part(), itemStack);
    }

    enum Part {
        FOOD("Food"),
        PLATE("Plate");

        private final String serializedName;

        Part(String serializedName) {
            this.serializedName = serializedName;
        }

        private static Part fromSerialized(String value) {
            return PLATE.serializedName.equalsIgnoreCase(value) ? PLATE : FOOD;
        }
    }
}
