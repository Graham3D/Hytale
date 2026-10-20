package com.inigmasgames.hytalerpg.input;

/** 0.7.0-pre.1 exposes no supported global C/K binding registration surface. */
public final class CommandOnlyRpgUiOpenInputAdapter implements RpgUiOpenInputAdapter {
    @Override public Availability availability() { return Availability.COMMAND_ONLY; }
}
