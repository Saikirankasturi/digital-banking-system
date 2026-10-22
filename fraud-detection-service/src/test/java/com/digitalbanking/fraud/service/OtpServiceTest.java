package com.digitalbanking.fraud.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.*;

class OtpServiceTest {

    private OtpService otpService;

    @BeforeEach
    void setUp() {
        StringRedisTemplate mockRedis = Mockito.mock(StringRedisTemplate.class);
        otpService = new OtpService(mockRedis);
    }

    @Test
    @DisplayName("Generate 6-digit OTP and verify successfully")
    void testOtpGenerationAndVerification() {
        String txId = "tx-sample-12345";
        String otp = otpService.generateOtp(txId);

        assertNotNull(otp);
        assertEquals(6, otp.length());

        // Incorrect OTP should fail
        assertFalse(otpService.verifyOtp(txId, "000000"));

        // Correct OTP should succeed
        assertTrue(otpService.verifyOtp(txId, otp));

        // Subsequent check fails (single-use OTP consumed)
        assertFalse(otpService.verifyOtp(txId, otp));
    }
}
