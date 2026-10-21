package com.acme.mail;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
class MailSender {

    @Value("${app.mail.host}")
    private String host;

    @Value("${app.mail.port:587}")
    private int port;

    @Value("${app.mail.from}")
    private String from;

    MailSender(@Value("${app.mail.retries:3}") int retries, @Value("${server.port}") int serverPort) {}
}
