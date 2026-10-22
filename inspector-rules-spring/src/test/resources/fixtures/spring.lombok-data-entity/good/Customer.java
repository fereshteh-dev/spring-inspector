package com.acme.customers;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
class Customer {

    @Id
    @EqualsAndHashCode.Include
    private Long id;

    private String name;
}
