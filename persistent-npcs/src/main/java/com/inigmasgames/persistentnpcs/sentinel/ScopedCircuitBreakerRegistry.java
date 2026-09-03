package com.inigmasgames.persistentnpcs.sentinel;

import static com.inigmasgames.persistentnpcs.sentinel.SentinelContracts.*;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** Bounded scoped circuits with one half-open probe after cooldown. */
public final class ScopedCircuitBreakerRegistry {
    private static final int MAX = 256;
    private final LinkedHashMap<String, Entry> entries = new LinkedHashMap<>(16, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Entry> eldest) {
            return size() > MAX;
        }
    };
    private final int threshold;
    private final Duration cooldown;
    private final Clock clock;

    public ScopedCircuitBreakerRegistry() {
        this(3, Duration.ofSeconds(30), Clock.systemUTC());
    }
    public ScopedCircuitBreakerRegistry(int threshold, Duration cooldown, Clock clock) {
        this.threshold = Math.max(2, threshold);
        this.cooldown = cooldown == null ? Duration.ofSeconds(30) : cooldown;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }
    public synchronized CircuitState failure(String key) {
        Instant now = clock.instant();
        Entry prior = entries.get(key);
        int failures = prior == null ? 1 : prior.failures() + 1;
        CircuitState state = failures >= threshold ? CircuitState.OPEN : CircuitState.CLOSED;
        entries.put(key, new Entry(state, failures, now, false));
        return state;
    }
    public synchronized CircuitState state(String key) {
        Entry value = entries.get(key);
        if (value == null) return CircuitState.CLOSED;
        if (value.state() == CircuitState.OPEN
                && !clock.instant().isBefore(value.changedAt().plus(cooldown))
                && !value.probeClaimed()) {
            entries.put(key, new Entry(CircuitState.HALF_OPEN, value.failures(),
                    clock.instant(), true));
            return CircuitState.HALF_OPEN;
        }
        return value.state();
    }
    public synchronized void verified(String key) { entries.remove(key); }
    public synchronized Map<String, CircuitState> snapshot() {
        var result = new LinkedHashMap<String, CircuitState>();
        entries.forEach((key, value) -> result.put(key, value.state()));
        return Map.copyOf(result);
    }
    private record Entry(CircuitState state, int failures, Instant changedAt,
            boolean probeClaimed) { }
}
