package com.acme.money;

import java.math.BigDecimal;
import java.util.Objects;

final class Money {

    private final BigDecimal amount;
    private final String currency;

    Money(BigDecimal amount, String currency) {
        this.amount = amount;
        this.currency = currency;
    }

    BigDecimal getAmount() {
        return amount;
    }

    String getCurrency() {
        return currency;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Money money && amount.equals(money.amount) && currency.equals(money.currency);
    }

    @Override
    public int hashCode() {
        return Objects.hash(amount, currency);
    }
}
