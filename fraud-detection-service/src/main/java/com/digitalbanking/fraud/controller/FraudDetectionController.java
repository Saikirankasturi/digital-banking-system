package com.digitalbanking.fraud.controller;

import com.digitalbanking.common.dto.ApiResponse;
import com.digitalbanking.common.dto.FraudEvaluationRequest;
import com.digitalbanking.common.dto.FraudEvaluationResult;
import com.digitalbanking.fraud.service.FraudDetectionService;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/fraud")
@RequiredArgsConstructor
public class FraudDetectionController {

    private final FraudDetectionService fraudDetectionService;

    @PostMapping("/evaluate")
    public ResponseEntity<ApiResponse<FraudEvaluationResult>> evaluate(@RequestBody FraudEvaluationRequest request) {
        FraudEvaluationResult result = fraudDetectionService.evaluateTransaction(request);
        return ResponseEntity.ok(ApiResponse.success(result, "Fraud evaluation completed"));
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<ApiResponse<OtpVerificationResponse>> verifyOtp(@RequestBody OtpVerificationRequest request) {
        boolean valid = fraudDetectionService.verifyOtp(request.getTransactionId(), request.getOtpCode());
        OtpVerificationResponse resp = OtpVerificationResponse.builder()
                .transactionId(request.getTransactionId())
                .valid(valid)
                .message(valid ? "OTP verified successfully. Transaction approved." : "Invalid or expired OTP.")
                .build();
        return ResponseEntity.ok(ApiResponse.success(resp));
    }

    @PostMapping("/generate-otp")
    public ResponseEntity<ApiResponse<String>> generateOtp(@RequestParam String transactionId) {
        String otp = fraudDetectionService.generateOtpForTransaction(transactionId);
        return ResponseEntity.ok(ApiResponse.success(otp, "OTP generated successfully"));
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OtpVerificationRequest {
        private String transactionId;
        private String otpCode;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OtpVerificationResponse {
        private String transactionId;
        private boolean valid;
        private String message;
    }
}
