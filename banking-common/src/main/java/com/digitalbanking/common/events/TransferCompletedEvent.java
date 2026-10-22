package com.digitalbanking.common.events;

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
public class TransferCompletedEvent {
    private String transactionId;
    private String sagaId;
    private String sourceAccountId;
    private String targetAccountId;
    private BigDecimal amount;
    private Currency currency;
    private long totalProcessingTimeMs;
    @Builder.Default
    private Instant timestamp = Instant.now();
}
