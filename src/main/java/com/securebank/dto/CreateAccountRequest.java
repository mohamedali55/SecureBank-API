package com.securebank.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

public record CreateAccountRequest(

        @Pattern(regexp = "^[A-Z]{3}$", message = "currency must be a 3-letter ISO code, e.g. USD")
        String currency,

        @DecimalMin(value = "0.00", message = "initialDeposit cannot be negative")
        @Digits(integer = 15, fraction = 4)
        BigDecimal initialDeposit) {

    public String currencyOrDefault() {
        return (currency == null || currency.isBlank()) ? "USD" : currency;
    }

    public BigDecimal initialDepositOrZero() {
        return initialDeposit == null ? BigDecimal.ZERO : initialDeposit;
    }
}
