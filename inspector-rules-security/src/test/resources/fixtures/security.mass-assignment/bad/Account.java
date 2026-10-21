package com.acme.accounts;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
class Account {

    @Id
    Long id;

    String email;

    boolean admin;
}
