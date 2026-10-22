package com.digitalbanking.gateway.ratelimit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimiterService {

    private final StringRedisTemplate stringRedisTemplate;

    private static final int MAX_REQUESTS_PER_MINUTE = 60;
    private final ConcurrentHashMap<String, InMemoryCounter> inMemoryStore = new ConcurrentHashMap<>();

    private record InMemoryCounter(AtomicInteger count, long resetTime) {}

    public boolean isAllowed(String clientIdentifier) {
        String key = "ratelimit:" + clientIdentifier;

        try {
            Long count = stringRedisTemplate.opsForValue().increment(key);
            if (count != null && count == 1) {
                stringRedisTemplate.expire(key, 60, TimeUnit.SECONDS);
            }
            if (count != null && count > MAX_REQUESTS_PER_MINUTE) {
                log.warn("Rate limit exceeded for client {}: {}/{}", clientIdentifier, count, MAX_REQUESTS_PER_MINUTE);
                return false;
            }
            return true;
        } catch (Exception e) {
            log.debug("Redis rate limiting unavailable ({}), falling back to in-memory counter", e.getMessage());
            return isAllowedInMemory(clientIdentifier);
        }
    }

    private synchronized boolean isAllowedInMemory(String clientIdentifier) {
        long now = System.currentTimeMillis();
        InMemoryCounter counter = inMemoryStore.get(clientIdentifier);

        if (counter == null || now > counter.resetTime()) {
            inMemoryStore.put(clientIdentifier, new InMemoryCounter(new AtomicInteger(1), now + 60000));
            return true;
        }

        int current = counter.count().incrementAndGet();
        return current <= MAX_REQUESTS_PER_MINUTE;
    }
}
