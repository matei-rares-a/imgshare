package com.example.backbase.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;

/**
 * Redis cache-aside helper with request coalescing.
 *
 * Pattern:
 *   1. getList / getMeta — check Redis first
 *   2. On miss: tryAcquireLock → compute → putList/putMeta → releaseLock
 *   3. If lock not acquired (stampede): wait ~120ms → retry cache
 *   4. On writes/deletes: evictMeta + evictAllLists
 *
 * All Redis calls wrapped in try/catch — degrades to cache-miss on failure.
 */
@Service
@ConditionalOnProperty(name = "storage.backend", havingValue = "s3", matchIfMissing = true)
public class CacheService {

    private static final Logger log = LoggerFactory.getLogger(CacheService.class);

    static final String LIST_PREFIX = "gallery:list:";
    static final String META_PREFIX = "gallery:meta:";
    static final String LOCK_PREFIX = "gallery:lock:";

    @Value("${cache.gallery-list.ttl-seconds:60}")
    private int listTtl;

    @Value("${cache.image-meta.ttl-seconds:3600}")
    private int metaTtl;

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public CacheService(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    // ---- list cache ----

    public Optional<String> getList(String key) {
        return getJson(LIST_PREFIX + key);
    }

    public void putList(String key, Object value) {
        putJson(LIST_PREFIX + key, value, Duration.ofSeconds(listTtl));
    }

    public void evictAllLists() {
        try {
            Set<String> keys = redis.keys(LIST_PREFIX + "*");
            if (keys != null && !keys.isEmpty()) redis.delete(keys);
        } catch (Exception e) {
            log.warn("Redis evictAllLists failed: {}", e.getMessage());
        }
    }

    // ---- metadata cache ----

    public Optional<String> getMeta(String objectKey) {
        return getJson(META_PREFIX + objectKey);
    }

    public void putMeta(String objectKey, Object value) {
        putJson(META_PREFIX + objectKey, value, Duration.ofSeconds(metaTtl));
    }

    public void evictMeta(String objectKey) {
        evict(META_PREFIX + objectKey);
    }

    // ---- request coalescing lock ----

    /**
     * Try to acquire a 500ms compute lock for this cache key.
     * Returns true → caller is responsible for computing + populating cache.
     * Returns false → another thread is computing; caller should wait + re-check.
     * If Redis is unavailable returns true (proceed without lock).
     */
    public boolean tryAcquireLock(String key) {
        try {
            return Boolean.TRUE.equals(
                    redis.opsForValue().setIfAbsent(LOCK_PREFIX + key, "1", Duration.ofMillis(500)));
        } catch (Exception e) {
            return true; // Redis down → let caller proceed
        }
    }

    public void releaseLock(String key) {
        try {
            redis.delete(LOCK_PREFIX + key);
        } catch (Exception e) {
            log.debug("Redis releaseLock failed for {}: {}", key, e.getMessage());
        }
    }

    // ---- deserialization helper ----

    public <T> Optional<T> deserialize(String json, TypeReference<T> type) {
        try {
            return Optional.ofNullable(objectMapper.readValue(json, type));
        } catch (Exception e) {
            log.warn("Cache deserialize failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    // ---- internals ----

    private Optional<String> getJson(String key) {
        try {
            return Optional.ofNullable(redis.opsForValue().get(key));
        } catch (Exception e) {
            log.warn("Redis GET failed key={}: {}", key, e.getMessage());
            return Optional.empty();
        }
    }

    private void putJson(String key, Object value, Duration ttl) {
        try {
            redis.opsForValue().set(key, objectMapper.writeValueAsString(value), ttl);
        } catch (Exception e) {
            log.warn("Redis SET failed key={}: {}", key, e.getMessage());
        }
    }

    private void evict(String key) {
        try {
            redis.delete(key);
        } catch (Exception e) {
            log.warn("Redis DEL failed key={}: {}", key, e.getMessage());
        }
    }
}
