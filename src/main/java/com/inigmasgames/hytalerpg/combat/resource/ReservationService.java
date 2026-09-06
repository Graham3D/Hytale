package com.inigmasgames.hytalerpg.combat.resource;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Server-authoritative Mana capacity reservation. Releasing capacity never mints current Mana. */
public final class ReservationService {
    private final Map<UUID, Map<String, Reservation>> byActor = new ConcurrentHashMap<>();

    public synchronized Reservation addPercentage(UUID actor, String ownerId, double fraction, NativeResourcePort resources) {
        if (!Double.isFinite(fraction) || fraction < 0.0 || fraction > 1.0) throw new IllegalArgumentException("Reservation fraction must be 0..1");
        return add(actor, new Reservation(ownerId, Form.PERCENTAGE, fraction), resources);
    }
    public synchronized Reservation addFixed(UUID actor, String ownerId, double amount, NativeResourcePort resources) {
        if (!Double.isFinite(amount) || amount < 0.0) throw new IllegalArgumentException("Reservation amount must be non-negative");
        return add(actor, new Reservation(ownerId, Form.FIXED, amount), resources);
    }
    private Reservation add(UUID actor, Reservation reservation, NativeResourcePort resources) {
        Map<String, Reservation> reservations = byActor.computeIfAbsent(actor, ignored -> new LinkedHashMap<>());
        Reservation prior = reservations.put(reservation.ownerId(), reservation);
        double max = resources.maximum(ResourceType.MANA);
        if (reserved(actor, max) > max + 1.0e-9) {
            if (prior == null) reservations.remove(reservation.ownerId()); else reservations.put(prior.ownerId(), prior);
            throw new IllegalStateException("Mana reservation would exceed total maximum");
        }
        resources.setCurrent(ResourceType.MANA, Math.min(resources.current(ResourceType.MANA), spendableMaximum(actor, max)));
        return reservation;
    }
    public synchronized boolean remove(UUID actor, String ownerId) {
        Map<String, Reservation> reservations = byActor.get(actor);
        if (reservations == null) return false;
        boolean removed = reservations.remove(ownerId) != null;
        if (reservations.isEmpty()) byActor.remove(actor);
        return removed;
    }
    public synchronized void removeAll(UUID actor) { byActor.remove(actor); }
    public synchronized double reserved(UUID actor, double totalMaximum) {
        return byActor.getOrDefault(actor, Map.of()).values().stream().mapToDouble(reservation ->
                reservation.form == Form.PERCENTAGE ? totalMaximum * reservation.value : reservation.value).sum();
    }
    public synchronized double spendableMaximum(UUID actor, double totalMaximum) {
        return Math.max(0.0, totalMaximum - reserved(actor, totalMaximum));
    }
    public synchronized Map<String, Reservation> reservations(UUID actor) {
        return Map.copyOf(byActor.getOrDefault(actor, Map.of()));
    }
    public enum Form { PERCENTAGE, FIXED }
    public record Reservation(String ownerId, Form form, double value) {
        public Reservation { if (ownerId == null || ownerId.isBlank()) throw new IllegalArgumentException("Reservation ownerId is required"); }
    }
}
