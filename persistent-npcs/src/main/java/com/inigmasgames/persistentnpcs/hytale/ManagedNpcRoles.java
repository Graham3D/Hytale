package com.inigmasgames.persistentnpcs.hytale;

import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Lightweight registry of profile-backed native role names. */
public final class ManagedNpcRoles {
    private static final Set<String> ROLE_KEYS = ConcurrentHashMap.newKeySet();

    private ManagedNpcRoles() { }

    public static boolean contains(String roleId) {
        return ROLE_KEYS.contains(key(roleId));
    }

    public static void register(String roleId) {
        if (roleId != null && !roleId.isBlank()) ROLE_KEYS.add(key(roleId));
    }

    public static void unregister(String roleId) {
        ROLE_KEYS.remove(key(roleId));
    }

    private static String key(String roleId) {
        return roleId == null ? "" : roleId.strip().toLowerCase(Locale.ROOT);
    }
}
