package com.acme.checkout;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class Checkout {

    public void placeOrder(Cart cart) {
        validate(cart);
        this.saveOrder(cart);
    }

    @Transactional
    public void saveOrder(Cart cart) {}

    private void validate(Cart cart) {}
}
