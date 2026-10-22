package com.acme.payments;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

@Service
class PaymentService {

    private final RestClient bank;
    private final ChatClient chat;
    private final PaymentRepository payments;

    PaymentService(RestClient bank, ChatClient chat, PaymentRepository payments) {
        this.bank = bank;
        this.chat = chat;
        this.payments = payments;
    }

    @Transactional
    public void pay(Payment payment) {
        payments.save(payment);
        bank.post().uri("/transfers").body(payment).retrieve().toBodilessEntity();
        bank.get().uri("/balance").retrieve().body(String.class);
    }

    @Transactional
    public String explain(Payment payment) {
        payments.save(payment);
        return chat.prompt().user("Explain this payment").call().content();
    }
}
