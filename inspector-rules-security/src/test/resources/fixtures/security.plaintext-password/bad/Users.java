package com.acme;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class Users {
    PasswordEncoder encoder() {
        return NoOpPasswordEncoder.getInstance();
    }

    UserDetails demo() {
        return User.withDefaultPasswordEncoder().username("a").password("b").build();
    }

    String stored() {
        return "{noop}letmein";
    }
}
