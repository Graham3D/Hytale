package com.inigmasgames.persistentnpcs.orbis;

/** Foreground work exhausted its bounded admission deadline before provider start. */
public final class ResourceStarvedException extends java.util.concurrent.TimeoutException {
    public ResourceStarvedException(String message) {
        super(message);
    }
}
