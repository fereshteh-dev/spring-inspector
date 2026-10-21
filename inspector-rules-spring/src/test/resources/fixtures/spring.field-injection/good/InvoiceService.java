package com.acme.billing;

import org.springframework.stereotype.Service;

@Service
public class InvoiceService {

    private final InvoiceRepository invoices;

    private final Clock clock;

    public InvoiceService(InvoiceRepository invoices, Clock clock) {
        this.invoices = invoices;
        this.clock = clock;
    }

    public Invoice find(long id) {
        return invoices.findById(id);
    }
}
