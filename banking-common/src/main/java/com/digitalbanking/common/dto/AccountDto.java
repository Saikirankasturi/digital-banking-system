package com.digitalbanking.common.dto;

import com.digitalbanking.common.enums.AccountStatus;
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
public class AccountDto {
    private String id;
    private String accountNumber;
    private String accountHolderName;
    private String email;
    private BigDecimal balance;
    private BigDecimal reservedBalance;
    private Currency currency;
    private AccountStatus status;
    private Instant createdAt;
    private Instant updatedAt;
}
