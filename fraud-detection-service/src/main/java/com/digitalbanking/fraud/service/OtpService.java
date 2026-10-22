package com.digitalbanking.fraud.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class OtpService {

    private final StringRedisTemplate stringRedisTemplate;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final long OTP_EXPIRATION_MINUTES = 5;

    // Fallback store if Redis is unavailable
    private final ConcurrentHashMap<String, OtpEntry> inMemoryOtpStore = new ConcurrentHashMap<>();

    private record OtpEntry(String code, long expiryTimeMillis) {}

    public String generateOtp(String transactionId) {
        int codeInt = 100000 + RANDOM.nextInt(900000);
        String otp = String.valueOf(codeInt);

        try {
            String redisKey = "otp:tx:" + transactionId;
            stringRedisTemplate.opsForValue().set(redisKey, otp, OTP_EXPIRATION_MINUTES, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.debug("Redis unavailable for OTP ({}), using in-memory store", e.getMessage());
            long expiry = System.currentTimeMillis() + (OTP_EXPIRATION_MINUTES * 60 * 1000);
            inMemoryOtpStore.put(transactionId, new OtpEntry(otp, expiry));
        }

        log.info("Generated OTP for transaction {}: [SECURE-OTP: {}] (Valid for {} mins)",
                transactionId, otp, OTP_EXPIRATION_MINUTES);
        return otp;
    }

    public boolean verifyOtp(String transactionId, String inputOtp) {
        if (inputOtp == null || inputOtp.isBlank()) {
            return false;
        }

        try {
            String redisKey = "otp:tx:" + transactionId;
            String storedOtp = stringRedisTemplate.opsForValue().get(redisKey);
            if (storedOtp != null && storedOtp.equals(inputOtp.trim())) {
                stringRedisTemplate.delete(redisKey);
                log.info("OTP verified successfully for transaction {}", transactionId);
                return true;
            }
        } catch (Exception e) {
            log.debug("Redis unavailable for OTP verification, checking in-memory store: {}", e.getMessage());
            OtpEntry entry = inMemoryOtpStore.get(transactionId);
            if (entry != null && System.currentTimeMillis() <= entry.expiryTimeMillis()) {
                if (entry.code().equals(inputOtp.trim())) {
                    inMemoryOtpStore.remove(transactionId);
                    log.info("In-memory OTP verified successfully for transaction {}", transactionId);
                    return true;
                }
            }
        }

        log.warn("Invalid or expired OTP attempt for transaction {}", transactionId);
        return false;
    }
}
