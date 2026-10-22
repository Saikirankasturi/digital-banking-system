package com.digitalbanking.transaction.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final StringRedisTemplate stringRedisTemplate;
    private static final long IDEMPOTENCY_TTL_HOURS = 24;

    private final ConcurrentHashMap<String, String> inMemoryStore = new ConcurrentHashMap<>();

    public boolean acquireLock(String idempotencyKey, String transactionId) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return true;
        }

        try {
            String redisKey = "idempotency:" + idempotencyKey;
            Boolean acquired = stringRedisTemplate.opsForValue()
                    .setIfAbsent(redisKey, transactionId, IDEMPOTENCY_TTL_HOURS, TimeUnit.HOURS);
            return Boolean.TRUE.equals(acquired);
        } catch (Exception e) {
            log.debug("Redis unavailable for idempotency ({}), using in-memory store", e.getMessage());
            return inMemoryStore.putIfAbsent(idempotencyKey, transactionId) == null;
        }
    }

    public String getExistingTransactionId(String idempotencyKey) {
        if (idempotencyKey == null) {
            return null;
        }
        try {
            String redisKey = "idempotency:" + idempotencyKey;
            return stringRedisTemplate.opsForValue().get(redisKey);
        } catch (Exception e) {
            return inMemoryStore.get(idempotencyKey);
        }
    }

    public void releaseLock(String idempotencyKey) {
        if (idempotencyKey == null) {
            return;
        }
        try {
            stringRedisTemplate.delete("idempotency:" + idempotencyKey);
        } catch (Exception e) {
            inMemoryStore.remove(idempotencyKey);
        }
    }
}
