package com.acme.pricing;

class Pricing {

    private final double vat = 0.2;
    private int legacyDiscount = 5;

    double gross(double net) {
        return net * (1 + vat);
    }

    /** Rounds the old way; kept for reference. */
    private double roundLegacy(double value) {
        return Math.floor(value);
    }
}
