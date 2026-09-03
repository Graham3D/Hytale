package com.inigmasgames.taverns;

import com.hypixel.hytale.server.core.permissions.HytalePermissions;
import com.hypixel.hytale.server.core.permissions.provider.PermissionProvider;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Grants only Selection Tool bounds updates, only while a player is in Zoning Editor. */
final class CoreModePermissionProvider implements PermissionProvider {
    private final Set<UUID> activePlayers = ConcurrentHashMap.newKeySet();

    void activate(UUID playerId) {
        activePlayers.add(playerId);
    }

    void deactivate(UUID playerId) {
        activePlayers.remove(playerId);
    }

    void clear() {
        activePlayers.clear();
    }

    @Override public String getName() { return "TavernsZoningEditor"; }
    @Override public Set<String> getUserPermissions(UUID uuid) {
        return activePlayers.contains(uuid)
                ? Set.of(HytalePermissions.EDITOR_SELECTION_USE)
                : Collections.emptySet();
    }

    @Override public void addUserPermissions(UUID uuid, Set<String> permissions) { }
    @Override public void removeUserPermissions(UUID uuid, Set<String> permissions) { }
    @Override public void addGroupPermissions(String group, Set<String> permissions) { }
    @Override public void removeGroupPermissions(String group, Set<String> permissions) { }
    @Override public Set<String> getGroupPermissions(String group) { return Collections.emptySet(); }
    @Override public void addUserToGroup(UUID uuid, String group) { }
    @Override public void removeUserFromGroup(UUID uuid, String group) { }
    @Override public Set<String> getGroupsForUser(UUID uuid) { return Collections.emptySet(); }
    @Override public void setUserGroup(UUID uuid, String group) { }
    @Override public String getGroupParent(String group) { return null; }
    @Override public Set<String> getAllRegisteredGroups() { return Collections.emptySet(); }
    @Override public Set<String> getEffectiveGroupPermissions(String group) { return Collections.emptySet(); }
}
