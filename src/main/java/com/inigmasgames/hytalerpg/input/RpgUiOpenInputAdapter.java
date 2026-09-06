package com.inigmasgames.hytalerpg.input;

/** Stable seam for a future configured Character/Link-tree open binding. */
public interface RpgUiOpenInputAdapter {
    Availability availability();
    enum Availability { COMMAND_ONLY, CONFIGURED_BINDING }
}
