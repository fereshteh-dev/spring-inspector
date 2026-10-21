package com.acme.orders;

import java.util.List;
import org.springframework.stereotype.Service;

@Service
class InvoiceService {

    private final OrderRepository orderRepository;

    InvoiceService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    long countLines(String status) {
        long lines = 0;
        for (Order order : orderRepository.findWithLinesByStatus(status)) {
            lines += order.getId();
        }
        return lines;
    }

    long countGiven(List<Order> orders) {
        long lines = 0;
        for (Order order : orders) {
            lines += order.getLines().size();
        }
        return lines;
    }
}
