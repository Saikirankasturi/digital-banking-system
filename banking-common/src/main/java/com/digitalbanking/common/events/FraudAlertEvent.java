package com.digitalbanking.common.events;

import com.digitalbanking.common.enums.Currency;
import com.digitalbanking.common.enums.FraudDecision;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FraudAlertEvent {
    private String alertId;
    private String transactionId;
    private String sourceAccountId;
    private String targetAccountId;
    private BigDecimal amount;
    private Currency currency;
    private FraudDecision decision;
    private int riskScore;
    private List<String> triggeredRules;
    private String ipAddress;
    private String deviceId;
    private String location;
    @Builder.Default
    private Instant alertTimestamp = Instant.now();
}
