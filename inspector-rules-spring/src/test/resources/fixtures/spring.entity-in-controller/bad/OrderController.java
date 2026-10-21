package com.acme.orders;

import java.util.List;
import org.springframework.http.ResponseEntity;
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
    Order get(@PathVariable long id) {
        return orders.findById(id).orElseThrow();
    }

    @GetMapping("/orders")
    ResponseEntity<List<Order>> all() {
        return ResponseEntity.ok(orders.findAll());
    }
}
