package com.inigmasgames.canvasui.api;

public final class GraphValidationException extends IllegalArgumentException {
    private final ConnectionResult result;
    public GraphValidationException(ConnectionResult result) {
        super(result.code() + (result.reason().isBlank() ? "" : ": " + result.reason()));
        this.result = result;
    }
    public ConnectionResult result() { return result; }
}
