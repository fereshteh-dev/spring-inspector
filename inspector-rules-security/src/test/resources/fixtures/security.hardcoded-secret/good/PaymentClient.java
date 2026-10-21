package com.acme.payments;

import org.springframework.beans.factory.annotation.Value;

class PaymentClient {

    private static final String TOKEN_HEADER = "X-Auth-Token";
    private static final String PASSWORD_PROPERTY = "payments.client.password";
    private static final String TOKEN_URL = "https://auth.example.com/oauth/token";

    private final String apiKey;
    private final String passwordHint = "Use at least twelve characters";
    private int tokenCount = 0;

    PaymentClient(@Value("${payments.api-key}") String apiKey) {
        this.apiKey = apiKey;
    }
}
