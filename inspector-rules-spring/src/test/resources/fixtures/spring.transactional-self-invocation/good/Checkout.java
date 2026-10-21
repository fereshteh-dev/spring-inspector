package com.acme.checkout;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class Checkout {

    private final OrderWriter writer;

    Checkout(OrderWriter writer) {
        this.writer = writer;
    }

    @Transactional
    public void placeOrder(Cart cart) {
        saveOrder(cart);
        writer.saveOrder(cart);
    }

    @Transactional
    public void saveOrder(Cart cart) {}
}
