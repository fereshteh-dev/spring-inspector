package com.acme.money;

import jakarta.persistence.Entity;
import java.math.BigDecimal;

record Money(BigDecimal amount, String currency) {}

@Entity
class Payment {

    private final Long id;

    Payment(Long id) {
        this.id = id;
    }

    Long getId() {
        return id;
    }
}

class Account {

    private final String owner;
    private BigDecimal balance = BigDecimal.ZERO;

    Account(String owner) {
        this.owner = owner;
    }

    void deposit(BigDecimal amount) {
        balance = balance.add(amount);
    }
}
