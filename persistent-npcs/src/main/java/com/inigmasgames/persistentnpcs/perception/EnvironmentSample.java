package com.inigmasgames.persistentnpcs.perception;

/** Detached block/fluid metadata captured on the owning world thread. */
public record EnvironmentSample(
        String assetId,
        String group,
        String material,
        String model,
        double x,
        double y,
        double z,
        boolean interactable,
        boolean craftingStation,
        boolean door,
        boolean container,
        boolean furniture,
        boolean light,
        boolean fluid) {
}
