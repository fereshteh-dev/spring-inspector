package com.acme.mail;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
class MailSender {

    private final MailProperties mail;

    MailSender(MailProperties mail, @Value("${server.port}") int serverPort) {
        this.mail = mail;
    }

    @ConfigurationProperties("app.mail")
    record MailProperties(String host, int port, String from, int retries) {}
}
