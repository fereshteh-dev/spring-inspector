package com.acme.assistant;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.security.access.prepost.PreAuthorize;

class AccountTools {

    private final AccountService accounts;

    AccountTools(AccountService accounts) {
        this.accounts = accounts;
    }

    @Tool(description = "Delete the customer's account")
    @PreAuthorize("hasRole('SUPPORT')")
    void deleteAccount(String accountId) {
        accounts.delete(accountId);
    }

    @Tool(description = "Refund an order once the user confirmed it")
    void refundOrder(String orderId, boolean confirmedByUser) {
        if (confirmedByUser) {
            accounts.refund(orderId);
        }
    }

    void updateCache() {}
}
