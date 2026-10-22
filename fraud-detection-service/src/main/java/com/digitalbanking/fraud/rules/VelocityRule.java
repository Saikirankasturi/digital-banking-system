package com.digitalbanking.fraud.rules;

import com.digitalbanking.common.dto.FraudEvaluationRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class VelocityRule implements FraudRule {

    private final StringRedisTemplate stringRedisTemplate;

    // Parameters
    private static final long WINDOW_SECONDS = 60;
    private static final int MAX_TRANSACTIONS_IN_WINDOW = 3;
    private static final int VELOCITY_RISK_SCORE = 50;

    // In-memory fallback if Redis is unreachable
    private final ConcurrentHashMap<String, ConcurrentLinkedQueue<Long>> inMemoryFallback = new ConcurrentHashMap<>();

    @Override
    public String getName() {
        return "VELOCITY_SLIDING_WINDOW_RULE";
    }

    @Override
    public int getOrder() {
        return 1;
    }

    @Override
    public RuleResult evaluate(FraudEvaluationRequest request) {
        String accountId = request.getSourceAccountId();
        long now = Instant.now().toEpochMilli();
        long windowStart = now - (WINDOW_SECONDS * 1000);

        try {
            String redisKey = "fraud:velocity:" + accountId;
            // 1. Remove timestamps older than sliding window
            stringRedisTemplate.opsForZSet().removeRangeByScore(redisKey, 0, windowStart);

            // 2. Add current transaction
            String member = request.getTransactionId() + ":" + now;
            stringRedisTemplate.opsForZSet().add(redisKey, member, now);
            stringRedisTemplate.expire(redisKey, WINDOW_SECONDS * 2, TimeUnit.SECONDS);

            // 3. Count transactions within current window
            Long count = stringRedisTemplate.opsForZSet().zCard(redisKey);
            long txCount = (count != null) ? count : 1;

            if (txCount > MAX_TRANSACTIONS_IN_WINDOW) {
                log.warn("Velocity fraud detected for account {}: {} transactions in {}s (limit: {})",
                        accountId, txCount, WINDOW_SECONDS, MAX_TRANSACTIONS_IN_WINDOW);
                return RuleResult.triggered(
                        getName(),
                        VELOCITY_RISK_SCORE,
                        String.format("High transaction velocity: %d transactions within %d seconds", txCount, WINDOW_SECONDS)
                );
            }
        } catch (Exception e) {
            log.debug("Redis unavailable for velocity check ({}), using fallback in-memory tracker", e.getMessage());
            long count = evaluateInMemory(accountId, now, windowStart);
            if (count > MAX_TRANSACTIONS_IN_WINDOW) {
                return RuleResult.triggered(
                        getName(),
                        VELOCITY_RISK_SCORE,
                        String.format("High transaction velocity (in-memory): %d transactions within %d seconds", count, WINDOW_SECONDS)
                );
            }
        }

        return RuleResult.clean(getName());
    }

    private synchronized long evaluateInMemory(String accountId, long now, long windowStart) {
        ConcurrentLinkedQueue<Long> queue = inMemoryFallback.computeIfAbsent(accountId, k -> new ConcurrentLinkedQueue<>());
        // Remove old entries
        while (!queue.isEmpty() && queue.peek() < windowStart) {
            queue.poll();
        }
        queue.add(now);
        return queue.size();
    }
}
