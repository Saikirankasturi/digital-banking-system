package com.digitalbanking.common.dto;

import com.digitalbanking.common.enums.Currency;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FraudEvaluationRequest {
    private String transactionId;
    private String sourceAccountId;
    private String targetAccountId;
    private BigDecimal amount;
    private Currency currency;
    private String deviceId;
    private String ipAddress;
    private String location;
    private BigDecimal currentBalance;
    @Builder.Default
    private Instant timestamp = Instant.now();
}
