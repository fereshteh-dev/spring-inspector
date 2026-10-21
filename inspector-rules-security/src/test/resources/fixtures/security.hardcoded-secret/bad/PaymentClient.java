package com.acme.payments;

class PaymentClient {

    private static final String API_KEY = "@STRIPE_KEY@";
    private static final String AWS_KEY = "@AWS_KEY@";

    private final String dbPassword = "Summer2024!x";
    private String clientSecret;

    void configure() {
        clientSecret = "c9f2-77ab-41d0-xyz9";
    }
}
