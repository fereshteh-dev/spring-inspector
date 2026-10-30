package com.acme;

import org.springframework.ai.tool.annotation.Tool;

class OrderTools {
    @Tool(description = "List orders")
    List<Order> orders(String userId) {
        return List.of();
    }

    @Tool(description = "Find a customer")
    Customer customer(long id, String tenantId) {
        return null;
    }
}
