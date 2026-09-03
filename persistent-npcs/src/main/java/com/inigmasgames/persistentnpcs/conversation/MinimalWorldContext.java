package com.inigmasgames.persistentnpcs.conversation;

public record MinimalWorldContext(String worldName, int approximateX, int approximateY, int approximateZ) {
    public String describe() {
        return "World " + worldName + ", near (" + approximateX + ", "
                + approximateY + ", " + approximateZ + ")";
    }
}

