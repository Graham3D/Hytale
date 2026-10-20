package com.inigmasgames.canvasui.api;

public record ConnectionResult(ConnectionCode code, String reason) {
    public ConnectionResult { reason = reason == null ? "" : reason; }
    public boolean allowed() { return code == ConnectionCode.ALLOW; }
    public static ConnectionResult allow() { return new ConnectionResult(ConnectionCode.ALLOW, ""); }
    public static ConnectionResult reject(ConnectionCode code, String reason) {
        if (code == ConnectionCode.ALLOW) throw new IllegalArgumentException("use allow()");
        return new ConnectionResult(code, reason);
    }
}
