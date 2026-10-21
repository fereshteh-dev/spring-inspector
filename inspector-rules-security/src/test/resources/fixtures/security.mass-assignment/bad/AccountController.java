package com.acme.accounts;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
class AccountController {

    private final AccountRepository accounts;

    AccountController(AccountRepository accounts) {
        this.accounts = accounts;
    }

    @PostMapping("/accounts")
    Account create(@RequestBody Account account) {
        return accounts.save(account);
    }
}
