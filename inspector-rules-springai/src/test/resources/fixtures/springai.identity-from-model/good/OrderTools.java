package com.acme;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.security.access.prepost.PreAuthorize;

class OrderTools {
    @Tool(description = "List orders")
    List<Order> orders(ToolContext context) {
        return List.of();
    }

    @Tool(description = "Find a customer")
    Customer customer(long customerId) {
        return null;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Tool(description = "List a user's orders, for administrators")
    List<Order> ordersOf(String userId) {
        return List.of();
    }

    List<Order> notATool(String userId) {
        return List.of();
    }
}
