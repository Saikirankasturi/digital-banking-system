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
public class AccountCreditedEvent {
    private String transactionId;
    private String sagaId;
    private String accountId;
    private BigDecimal amount;
    private Currency currency;
    private BigDecimal newBalance;
    @Builder.Default
    private Instant timestamp = Instant.now();
}
