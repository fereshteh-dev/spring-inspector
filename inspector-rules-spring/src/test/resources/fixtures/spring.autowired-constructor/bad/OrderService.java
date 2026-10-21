package com.acme.orders;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
class OrderService {

    private final OrderRepository orders;

    @Autowired
    OrderService(OrderRepository orders) {
        this.orders = orders;
    }
}
