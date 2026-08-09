package com.example.backbase.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory idempotency key store.
 * Prevents duplicate work on retried upload/delete requests.
 * For multi-node deployments replace with a Redis-backed implementation.
 *
 * Usage: client sends X-Idempotency-Key header; any subsequent request
 * with the same key within ttlHours returns the cached response.
 */
@Service
public class IdempotencyService {

    @Value("${idempotency.ttl-hours:24}")
    private int ttlHours;

    private record Entry(Map<String, Object> response, Instant createdAt) {}

    private final ConcurrentHashMap<String, Entry> store = new ConcurrentHashMap<>();

    /** Returns true if this key was already processed and is not yet expired. */
    public boolean exists(String key) {
        evictExpired();
        return store.containsKey(key);
    }

    /** Returns cached response for an already-processed key, or null. */
    public Map<String, Object> get(String key) {
        Entry entry = store.get(key);
        return entry != null ? entry.response() : null;
    }

    /** Record the result of a completed operation so retries return the same response. */
    public void store(String key, Map<String, Object> response) {
        store.put(key, new Entry(Map.copyOf(response), Instant.now()));
    }

    private void evictExpired() {
        Instant cutoff = Instant.now().minusSeconds((long) ttlHours * 3600);
        store.entrySet().removeIf(e -> e.getValue().createdAt().isBefore(cutoff));
    }
}
