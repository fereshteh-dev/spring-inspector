package com.acme.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class LoginService {

    private static final Logger log = LoggerFactory.getLogger(LoginService.class);

    boolean login(String username, String password) {
        log.debug("Login attempt {} with {}", username, password);
        return true;
    }

    void refresh(Credentials credentials) {
        System.out.println("Refreshing token " + credentials.getAccessToken());
    }
}
