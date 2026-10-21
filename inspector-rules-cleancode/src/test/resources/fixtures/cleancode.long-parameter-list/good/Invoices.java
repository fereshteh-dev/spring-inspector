package com.acme.invoices;

import java.math.BigDecimal;
import java.time.LocalDate;

class Invoices {

    private final Repository repository;

    Invoices(Repository repository, Clock clock, Mailer mailer, Pdf pdf, Audit audit, Tax tax, Numbering numbering) {
        this.repository = repository;
    }

    Invoice create(String customer, Address address, BigDecimal amount, LocalDate due) {
        return null;
    }

    record Address(String street, String city, String zip, String country) {}
}
