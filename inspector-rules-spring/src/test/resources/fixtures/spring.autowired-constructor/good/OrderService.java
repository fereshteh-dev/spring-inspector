package com.acme.orders;

import org.springframework.stereotype.Service;

@Service
class OrderService {

    private final OrderRepository orders;

    OrderService(OrderRepository orders) {
        this.orders = orders;
    }
}
