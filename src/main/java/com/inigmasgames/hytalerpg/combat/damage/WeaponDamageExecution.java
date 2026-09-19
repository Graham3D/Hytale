package com.inigmasgames.hytalerpg.combat.damage;

import java.util.*;

/** Immutable input to an execution-wide conversion. No victim state, ECS handles or random rerolls.
 * The producing execution adapter must supply its COMPLETE resolved source component set; a
 * per-victim Damage event, nominal asset value or a second calculator evaluation is not that input.
 * This contract does not by itself authenticate native producers. */
public record WeaponDamageExecution(Identity identity, String itemId, Delivery delivery,
        boolean derived, boolean canProc, boolean critical, List<Component> components) {
    public static final int MAX_COMPONENTS = 32;
    public enum Delivery { NATIVE_MELEE, NATIVE_RANGED, RPG_WEAPON, SPELL, PERIODIC, SUMMON, TRAP, ENVIRONMENT, REFLECTION, AURA }
    public enum Provenance { WEAPON, WEAPON_AFFIX, FLAME_WEAPON, SPELL, BURN, PERIODIC, SUMMON, TRAP, ENVIRONMENT, REFLECTION, DERIVED }

    /** Authored ordinal belongs to the producer's execution, not the victim index or a time bucket. */
    public record Identity(UUID worldId, UUID actorId, String rootId, String executionId, String authoredTickId) {
        public Identity {
            Objects.requireNonNull(worldId); Objects.requireNonNull(actorId);
            rootId = id(rootId); executionId = id(executionId); authoredTickId = id(authoredTickId);
        }
    }
    public record Component(String componentId, String channel, double sourceAmount,
            Provenance provenance, boolean directWeaponHit, boolean canProc) {
        public Component {
            componentId = id(componentId); channel = id(channel); Objects.requireNonNull(provenance);
            if (!channel.matches("[A-Z][A-Z0-9_]*")) throw new IllegalArgumentException("INVALID_DAMAGE_CHANNEL");
            if (!Double.isFinite(sourceAmount) || sourceAmount < 0 || sourceAmount > Float.MAX_VALUE)
                throw new IllegalArgumentException("INVALID_SOURCE_COMPONENT_AMOUNT");
            if (directWeaponHit && !weapon(provenance))
                throw new IllegalArgumentException("NONWEAPON_COMPONENT_CANNOT_CLAIM_WEAPON_PROVENANCE");
        }
        private static boolean weapon(Provenance p) {
            return p == Provenance.WEAPON || p == Provenance.WEAPON_AFFIX || p == Provenance.FLAME_WEAPON;
        }
    }
    public WeaponDamageExecution {
        Objects.requireNonNull(identity); Objects.requireNonNull(delivery); itemId = id(itemId);
        Objects.requireNonNull(components);
        if (components.isEmpty() || components.size() > MAX_COMPONENTS)
            throw new IllegalArgumentException("SOURCE_COMPONENT_BUDGET");
        var unique = new TreeMap<String, Component>();
        for (var component : components) {
            Objects.requireNonNull(component);
            var prior = unique.putIfAbsent(component.componentId(), component);
            if (prior != null && !prior.equals(component))
                throw new IllegalArgumentException("CONFLICTING_SOURCE_COMPONENT_ID");
        }
        components = List.copyOf(unique.values());
        double total = 0;
        for (var component : components) total += component.sourceAmount();
        if (!Double.isFinite(total) || total > Float.MAX_VALUE)
            throw new IllegalArgumentException("SOURCE_EXECUTION_AMOUNT_OVERFLOW");
    }
    public boolean eligible(Component component) {
        return !derived && canProc && switch (delivery) {
            case NATIVE_MELEE, NATIVE_RANGED, RPG_WEAPON -> component.directWeaponHit()
                    && component.canProc() && Component.weapon(component.provenance()) && component.channel().equals("FIRE");
            default -> false;
        };
    }
    public double sourceFire() {
        return components.stream().filter(this::eligible).mapToDouble(Component::sourceAmount).sum();
    }
    public Component component(String componentId) {
        return components.stream().filter(c -> c.componentId().equals(componentId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("UNKNOWN_SOURCE_COMPONENT"));
    }
    private static String id(String value) {
        if (value == null || value.isBlank() || value.length() > 512 || value.chars().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("INVALID_SOURCE_ID");
        return value;
    }
}
