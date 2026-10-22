package com.digitalbanking.common.dto;

import com.digitalbanking.common.enums.Currency;
import com.digitalbanking.common.enums.TransactionStatus;
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
public class TransferResponse {
    private String transactionId;
    private String sagaId;
    private TransactionStatus status;
    private String sourceAccountId;
    private String targetAccountId;
    private BigDecimal amount;
    private Currency currency;
    private int fraudRiskScore;
    private String message;
    private Instant createdAt;
    private Instant completedAt;
}
