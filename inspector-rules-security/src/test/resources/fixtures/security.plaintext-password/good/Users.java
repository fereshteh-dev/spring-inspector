package com.acme;

import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

class Users {
    PasswordEncoder encoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    Object notTheSpringEncoder(Pool pool) {
        return pool.getInstance();
    }

    interface Pool {
        Object getInstance();
    }
}
