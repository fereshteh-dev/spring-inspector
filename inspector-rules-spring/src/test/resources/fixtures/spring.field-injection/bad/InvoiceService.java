package com.acme.billing;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class InvoiceService {

    @Autowired
    private InvoiceRepository invoices;

    @Autowired
    private Clock clock;

    public Invoice find(long id) {
        return invoices.findById(id);
    }
}
