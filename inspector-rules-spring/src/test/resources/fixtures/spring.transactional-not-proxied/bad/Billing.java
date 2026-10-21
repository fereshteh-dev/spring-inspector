package com.acme.billing;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class Billing {

    @Transactional
    private void charge(long invoiceId) {}

    @Transactional
    public final void refund(long invoiceId) {}

    @Transactional
    static void archive() {}
}

@Service
final class Ledger {

    @Transactional
    public void book(long entryId) {}
}
