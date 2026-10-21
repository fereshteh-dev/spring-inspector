package com.acme.customers;

import java.util.List;
import java.util.Optional;

class Customers {

    Optional<Customer> byEmail(String email) {
        if (email.isBlank()) {
            return null;
        }
        return Optional.empty();
    }

    List<Customer> inCity(String city) {
        return null;
    }
}
