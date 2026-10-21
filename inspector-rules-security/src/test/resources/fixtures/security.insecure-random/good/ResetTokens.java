package com.acme.accounts;

import java.security.SecureRandom;
import java.util.Random;

class ResetTokens {

    private final SecureRandom tokenRandom = new SecureRandom();
    private final Random shuffle = new Random();

    String newResetToken() {
        return Long.toHexString(tokenRandom.nextLong());
    }

    int pickTip(int tips) {
        return shuffle.nextInt(tips);
    }
}
