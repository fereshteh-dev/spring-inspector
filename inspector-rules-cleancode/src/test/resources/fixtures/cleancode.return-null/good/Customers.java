package com.acme.customers;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

class Customers {

    Optional<Customer> byEmail(String email) {
        if (email.isBlank()) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    List<Customer> inCity(String city) {
        return List.of();
    }

    List<Function<String, Customer>> lookups() {
        return List.of(email -> {
            return null;
        });
    }

    Customer find(String id) {
        return null;
    }
}
