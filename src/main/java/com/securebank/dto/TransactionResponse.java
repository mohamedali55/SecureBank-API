package com.securebank.dto;

import com.securebank.domain.Account;
import com.securebank.domain.Transaction;
import com.securebank.domain.TransactionStatus;
import com.securebank.domain.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;

public record TransactionResponse(
        Long id,
        String reference,
        String fromAccountNumber,
        String toAccountNumber,
        BigDecimal amount,
        TransactionType type,
        TransactionStatus status,
        String description,
        Instant createdAt) {

    public static TransactionResponse from(Transaction tx) {
        return new TransactionResponse(
                tx.getId(),
                tx.getReference(),
                accountNumberOrNull(tx.getFromAccount()),
                accountNumberOrNull(tx.getToAccount()),
                tx.getAmount(),
                tx.getType(),
                tx.getStatus(),
                tx.getDescription(),
                tx.getCreatedAt());
    }

    private static String accountNumberOrNull(Account account) {
        return account == null ? null : account.getAccountNumber();
    }
}
