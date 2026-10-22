package com.acme.payments;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

@Service
class PaymentService {

    private final RestClient bank;
    private final PaymentRepository payments;

    PaymentService(RestClient bank, PaymentRepository payments) {
        this.bank = bank;
        this.payments = payments;
    }

    public void pay(Payment payment) {
        bank.post().uri("/transfers").body(payment).retrieve().toBodilessEntity();
        record(payment);
    }

    @Transactional
    public void record(Payment payment) {
        payments.save(payment);
    }
}
