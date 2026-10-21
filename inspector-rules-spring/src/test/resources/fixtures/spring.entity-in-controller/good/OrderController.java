package com.acme.orders;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
class OrderController {

    private final OrderRepository orders;

    OrderController(OrderRepository orders) {
        this.orders = orders;
    }

    @GetMapping("/orders/{id}")
    OrderView get(@PathVariable long id) {
        Order order = orders.findById(id).orElseThrow();
        return new OrderView(order.getId());
    }

    Order internal(long id) {
        return orders.findById(id).orElseThrow();
    }

    record OrderView(Long id) {}
}
