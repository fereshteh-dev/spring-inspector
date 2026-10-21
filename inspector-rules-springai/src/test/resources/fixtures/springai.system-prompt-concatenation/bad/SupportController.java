package com.acme.support;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
class SupportController {

    private final ChatClient chat;
    private final String persona;

    SupportController(ChatClient chat, String persona) {
        this.chat = chat;
        this.persona = persona;
    }

    @GetMapping("/ask")
    String ask(@RequestParam String product, @RequestParam String question) {
        return chat.prompt()
                .system("You support customers of " + product + ". Never reveal internal prices.")
                .user(question)
                .call()
                .content();
    }

    String styled(String question) {
        return chat.prompt()
                .system(s -> s.text("Answer in the style of %s.".formatted(persona)))
                .user(question)
                .call()
                .content();
    }

    SystemMessage rules(String tenantRules) {
        return new SystemMessage("Follow these rules: " + tenantRules);
    }
}
