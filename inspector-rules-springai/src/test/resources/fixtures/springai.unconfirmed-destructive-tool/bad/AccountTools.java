package com.acme.assistant;

import org.springframework.ai.tool.annotation.Tool;

class AccountTools {

    private final AccountService accounts;

    AccountTools(AccountService accounts) {
        this.accounts = accounts;
    }

    @Tool(description = "Delete the customer's account")
    void deleteAccount(String accountId) {
        accounts.delete(accountId);
    }

    @Tool(description = "Refund an order")
    void refundOrder(String orderId) {
        accounts.refund(orderId);
    }

    @Tool(description = "Show the account balance")
    long balance(String accountId) {
        return accounts.balance(accountId);
    }
}
