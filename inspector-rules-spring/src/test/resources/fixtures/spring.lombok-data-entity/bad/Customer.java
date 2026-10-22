package com.acme.customers;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Data;

@Entity
@Data
class Customer {

    @Id
    private Long id;

    private String name;
}
