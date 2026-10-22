package com.digitalbanking.payment.controller;

import com.digitalbanking.common.dto.ApiResponse;
import com.digitalbanking.common.enums.Currency;
import com.digitalbanking.payment.domain.PaymentOrder;
import com.digitalbanking.payment.service.PaymentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/orders")
    public ResponseEntity<ApiResponse<PaymentOrder>> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        PaymentOrder order = paymentService.createOrder(request.getAccountId(), request.getAmount(), request.getCurrency());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(order, "Payment gateway order created"));
    }

    @PostMapping("/webhook")
    public ResponseEntity<ApiResponse<PaymentOrder>> handleWebhook(
            @RequestHeader(value = "X-Gateway-Signature", required = false) String signature,
            @RequestBody WebhookPayload payload) {

        PaymentOrder updatedOrder = paymentService.processWebhook(
                payload.getGatewayOrderId(),
                payload.getPaymentId(),
                payload.isSuccess(),
                payload.getFailureReason()
        );
        return ResponseEntity.ok(ApiResponse.success(updatedOrder, "Webhook processed successfully"));
    }

    @GetMapping("/orders/{id}")
    public ResponseEntity<ApiResponse<PaymentOrder>> getOrder(@PathVariable String id) {
        PaymentOrder order = paymentService.getOrder(id);
        return ResponseEntity.ok(ApiResponse.success(order));
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateOrderRequest {
        @NotBlank(message = "Account ID is required")
        private String accountId;

        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.01", message = "Amount must be positive")
        private BigDecimal amount;

        private Currency currency;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WebhookPayload {
        private String gatewayOrderId;
        private String paymentId;
        private boolean success;
        private String failureReason;
    }
}
