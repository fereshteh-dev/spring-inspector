package com.acme.accounts;

import java.util.Random;

class ResetTokens {

    private final Random tokenRandom = new Random();

    String newResetToken() {
        return Long.toHexString(tokenRandom.nextLong());
    }

    int verificationCode() {
        return (int) (Math.random() * 1_000_000);
    }
}
