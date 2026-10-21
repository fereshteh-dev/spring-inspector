package com.acme.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class LoginService {

    private static final Logger log = LoggerFactory.getLogger(LoginService.class);
    private static final String TOKEN_HEADER = "Authorization";

    boolean login(String username, String password) {
        log.debug("Login attempt for {}", username);
        log.info("Reading credentials from header {}", TOKEN_HEADER);
        return true;
    }

    void usage(int tokenCount) {
        log.info("Used {} tokens", tokenCount);
    }
}
