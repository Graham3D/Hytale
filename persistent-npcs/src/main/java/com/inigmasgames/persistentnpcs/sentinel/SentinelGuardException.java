package com.inigmasgames.persistentnpcs.sentinel;

/** Internal bounded containment signal; never rendered verbatim to player dialogue. */
public final class SentinelGuardException extends RuntimeException {
    public SentinelGuardException(String invariantId, String reason) {
        super("SENTINEL_CONTAINED " + invariantId + " " + reason);
    }
}
