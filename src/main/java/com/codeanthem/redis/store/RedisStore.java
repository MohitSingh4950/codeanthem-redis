package com.codeanthem.redis.store;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * The actual "database". Real Redis keeps everything in memory -- that's WHY it's fast.
 * We do the same thing here with a ConcurrentHashMap so multiple client threads can
 * read/write safely at the same time.
 *
 * We track two maps:
 *   data           -> key to value
 *   expiryAtMillis -> key to the epoch-millis timestamp when it should expire (if any)
 *
 * A background thread sweeps expired keys every second, and reads also do
 * "lazy expiry" (check-on-read) so a key never gets returned after it should be gone.
 */
@Component
public class RedisStore {

    private final Map<String, String> data = new ConcurrentHashMap<>();
    private final Map<String, Long> expiryAtMillis = new ConcurrentHashMap<>();

    public RedisStore() {
        // Background sweeper, mimics Redis's own active expiry cycle.
        ScheduledExecutorService sweeper = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "redis-expiry-sweeper");
            t.setDaemon(true);
            return t;
        });
        sweeper.scheduleAtFixedRate(this::sweepExpiredKeys, 1, 1, TimeUnit.SECONDS);
    }

    public void set(String key, String value) {
        data.put(key, value);
        expiryAtMillis.remove(key); // SET without TTL clears any previous expiry
    }

    public void setWithTtl(String key, String value, long ttlSeconds) {
        data.put(key, value);
        expiryAtMillis.put(key, System.currentTimeMillis() + ttlSeconds * 1000);
    }

    public String get(String key) {
        expireIfNeeded(key);
        return data.get(key);
    }

    public boolean exists(String key) {
        expireIfNeeded(key);
        return data.containsKey(key);
    }

    public boolean delete(String key) {
        expiryAtMillis.remove(key);
        return data.remove(key) != null;
    }

    public boolean expire(String key, long ttlSeconds) {
        if (!data.containsKey(key)) {
            return false;
        }
        expiryAtMillis.put(key, System.currentTimeMillis() + ttlSeconds * 1000);
        return true;
    }

    /** Returns seconds until expiry, -1 if key has no TTL, -2 if key doesn't exist. */
    public long ttl(String key) {
        expireIfNeeded(key);
        if (!data.containsKey(key)) {
            return -2;
        }
        Long expiresAt = expiryAtMillis.get(key);
        if (expiresAt == null) {
            return -1;
        }
        return Math.max(0, (expiresAt - System.currentTimeMillis()) / 1000);
    }

    public long incr(String key) {
        return data.compute(key, (k, currentValue) -> {
            long newValue = (currentValue == null ? 0 : Long.parseLong(currentValue)) + 1;
            return String.valueOf(newValue);
        }) != null ? Long.parseLong(data.get(key)) : 0;
    }

    public long decr(String key) {
        return data.compute(key, (k, currentValue) -> {
            long newValue = (currentValue == null ? 0 : Long.parseLong(currentValue)) - 1;
            return String.valueOf(newValue);
        }) != null ? Long.parseLong(data.get(key)) : 0;
    }

    public List<String> keys() {
        sweepExpiredKeys();
        return data.keySet().stream().collect(Collectors.toList());
    }

    public void flushAll() {
        data.clear();
        expiryAtMillis.clear();
    }

    public int dbSize() {
        sweepExpiredKeys();
        return data.size();
    }

    private void expireIfNeeded(String key) {
        Long expiresAt = expiryAtMillis.get(key);
        if (expiresAt != null && System.currentTimeMillis() >= expiresAt) {
            data.remove(key);
            expiryAtMillis.remove(key);
        }
    }

    private void sweepExpiredKeys() {
        long now = System.currentTimeMillis();
        for (String key : expiryAtMillis.keySet()) {
            Long expiresAt = expiryAtMillis.get(key);
            if (expiresAt != null && now >= expiresAt) {
                data.remove(key);
                expiryAtMillis.remove(key);
            }
        }
    }
}
