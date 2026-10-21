package com.acme.accounts;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
class AccountController {

    private final AccountService accounts;

    AccountController(AccountService accounts) {
        this.accounts = accounts;
    }

    @PostMapping("/accounts")
    AccountView create(@RequestBody NewAccount request) {
        return accounts.create(request);
    }

    record NewAccount(String email) {}
}
