package com.inigmasgames.hytalerpg.combat.resource;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
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
        Map<String, Reservation> prior = byActor.getOrDefault(actor, Map.of());
        Map<String, Reservation> candidate = new LinkedHashMap<>(prior);
        candidate.put(reservation.ownerId(), reservation);
        double max = resources.maximum(ResourceType.MANA);
        requireMaximum(max);
        double nextReserved = sum(candidate, max);
        if (nextReserved > max + 1.0e-9)
            throw new IllegalStateException("Mana reservation would exceed total maximum");
        double debit = Math.max(0, nextReserved - sum(prior, max));
        double current = resources.current(ResourceType.MANA);
        if (!Double.isFinite(current) || current + 1.0e-9 < debit)
            throw new IllegalStateException("Insufficient current Mana for reservation");
        double after = Math.min(current - debit, Math.max(0, max - nextReserved));
        // Publish the allocation only after the native debit is observed. Resizing down never credits Mana.
        try {
            resources.setCurrent(ResourceType.MANA, after);
            if (Math.abs(resources.current(ResourceType.MANA) - after) > 1.0e-4)
                throw new IllegalStateException("Native Mana reservation debit was not applied");
            resources.setReservedMana(nextReserved);
        } catch (RuntimeException failure) {
            try { resources.setReservedMana(sum(prior,max)); resources.setCurrent(ResourceType.MANA, current); }
            catch (RuntimeException rollback) { failure.addSuppressed(rollback); }
            throw failure;
        }
        byActor.put(actor, candidate);
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
    public synchronized boolean remove(UUID actor,String ownerId,NativeResourcePort resources){
        Map<String,Reservation> candidate=new LinkedHashMap<>(byActor.getOrDefault(actor,Map.of()));
        if(candidate.remove(ownerId)==null)return false;
        resources.setReservedMana(sum(candidate,resources.maximum(ResourceType.MANA)));
        if(candidate.isEmpty())byActor.remove(actor);else byActor.put(actor,candidate);
        return true;
    }
    public synchronized void removeAll(UUID actor,NativeResourcePort resources){
        resources.setReservedMana(0);byActor.remove(actor);
    }
    /** Compensation for a bounded activation/allocation transaction whose durable commit failed.
     * This is not an unreserve/recovery operation and must never be exposed as a gameplay refill. */
    public synchronized void rollbackMutation(UUID actor,Map<String,Reservation> previous,double previousCurrent,NativeResourcePort resources){
        double max=resources.maximum(ResourceType.MANA),amount=sum(previous,max);
        if(!Double.isFinite(previousCurrent)||previousCurrent<0||amount>max+1e-9||previousCurrent>max-amount+1e-4)
            throw new IllegalArgumentException("Invalid reservation rollback snapshot");
        resources.setReservedMana(amount);
        resources.setCurrent(ResourceType.MANA,previousCurrent);
        if(Math.abs(resources.current(ResourceType.MANA)-previousCurrent)>1e-4)throw new IllegalStateException("Reservation rollback native write rejected");
        if(previous.isEmpty())byActor.remove(actor);else byActor.put(actor,new LinkedHashMap<>(previous));
    }
    public synchronized double reserved(UUID actor, double totalMaximum) {
        requireMaximum(totalMaximum);
        return sum(byActor.getOrDefault(actor, Map.of()), totalMaximum);
    }
    private static double sum(Map<String, Reservation> allocations, double totalMaximum) {
        return allocations.values().stream().mapToDouble(reservation ->
                reservation.form == Form.PERCENTAGE ? totalMaximum * reservation.value : reservation.value).sum();
    }
    public synchronized double spendableMaximum(UUID actor, double totalMaximum) {
        return Math.max(0.0, totalMaximum - reserved(actor, totalMaximum));
    }
    public synchronized Map<String, Reservation> reservations(UUID actor) {
        return java.util.Collections.unmodifiableMap(new LinkedHashMap<>(byActor.getOrDefault(actor, Map.of())));
    }
    /** Maximum changes cancel newest allocations first. A larger maximum does not grant current Mana. */
    public synchronized List<String> reconcileMaximum(UUID actor, NativeResourcePort resources) {
        double max = resources.maximum(ResourceType.MANA);
        requireMaximum(max);
        Map<String, Reservation> candidate = new LinkedHashMap<>(byActor.getOrDefault(actor, Map.of()));
        List<String> cancelled = new ArrayList<>();
        while (sum(candidate, max) > max + 1.0e-9 && !candidate.isEmpty()) {
            String newest = new ArrayList<>(candidate.keySet()).getLast();
            candidate.remove(newest);
            cancelled.add(newest);
        }
        double before = resources.current(ResourceType.MANA);
        double after = Math.min(before, Math.max(0, max - sum(candidate, max)));
        if(Math.abs(before-after)>1e-9)resources.setCurrent(ResourceType.MANA, after);
        if (Math.abs(resources.current(ResourceType.MANA) - after) > 1.0e-4)
            throw new IllegalStateException("Native Mana reservation cap was not applied");
        resources.setReservedMana(sum(candidate,max));
        if (candidate.isEmpty()) byActor.remove(actor); else byActor.put(actor, candidate);
        return List.copyOf(cancelled);
    }
    private static void requireMaximum(double maximum) {
        if (!Double.isFinite(maximum) || maximum < 0) throw new IllegalArgumentException("Invalid total Mana maximum");
    }
    public enum Form { PERCENTAGE, FIXED }
    public record Reservation(String ownerId, Form form, double value) {
        public Reservation {
            if (ownerId == null || ownerId.isBlank()) throw new IllegalArgumentException("Reservation ownerId is required");
            if (form == null || !Double.isFinite(value) || value < 0 || (form == Form.PERCENTAGE && value > 1))
                throw new IllegalArgumentException("Invalid reservation");
        }
    }
}
