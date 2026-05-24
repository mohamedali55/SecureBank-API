package com.securebank.dto;

import com.securebank.domain.Account;
import com.securebank.domain.AccountStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record AccountResponse(
        Long id,
        String accountNumber,
        String ownerUsername,
        BigDecimal balance,
        String currency,
        AccountStatus status,
        Instant createdAt) {

    public static AccountResponse from(Account account) {
        return new AccountResponse(
                account.getId(),
                account.getAccountNumber(),
                account.getOwner().getUsername(),
                account.getBalance(),
                account.getCurrency(),
                account.getStatus(),
                account.getCreatedAt());
    }
}
