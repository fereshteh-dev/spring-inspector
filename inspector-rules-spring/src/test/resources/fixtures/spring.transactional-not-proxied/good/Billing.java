package com.acme.billing;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class Billing {

    @Transactional
    public void charge(long invoiceId) {}

    @Transactional
    void refund(long invoiceId) {}

    private void helper() {}
}
