package com.inigmasgames.canvasui.api;

@FunctionalInterface
public interface ConnectionPolicy {
    ConnectionResult validate(CanvasNode source, CanvasPort sourcePort,
                              CanvasNode target, CanvasPort targetPort);

    static ConnectionPolicy allowAll() { return (source, sourcePort, target, targetPort) -> ConnectionResult.allow(); }
}
