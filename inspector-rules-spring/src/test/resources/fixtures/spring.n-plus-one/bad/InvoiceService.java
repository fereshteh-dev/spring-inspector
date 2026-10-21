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
        List<Order> open = orderRepository.findByStatus(status);
        for (Order order : open) {
            lines += order.getLines().size();
            String name = order.getCustomer().toString();
            String warehouse = order.getWarehouse().toString();
        }
        return lines;
    }
}
