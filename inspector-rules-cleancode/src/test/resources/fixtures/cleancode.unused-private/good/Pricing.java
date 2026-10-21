package com.acme.pricing;

import java.io.Serializable;
import org.springframework.beans.factory.annotation.Value;

class Pricing implements Serializable {

    private static final long serialVersionUID = 1L;

    private final double vat = 0.2;

    @Value("${pricing.currency}")
    private String currency;

    double gross(double net) {
        return round(net * (1 + vat));
    }

    private double round(double value) {
        return Math.round(value * 100) / 100.0;
    }
}
