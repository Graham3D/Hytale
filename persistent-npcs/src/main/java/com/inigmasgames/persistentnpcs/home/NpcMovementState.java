package com.inigmasgames.persistentnpcs.home;

/** The single authoritative locomotion owner for Mara's home/follow lifecycle. */
public enum NpcMovementState {
    IDLE_HOME,
    INVESTIGATING,
    RETURNING_HOME,
    FOLLOWING_PLAYER
}
