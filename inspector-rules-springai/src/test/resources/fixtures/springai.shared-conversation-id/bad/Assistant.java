package com.acme.assistant;

import static org.springframework.ai.chat.memory.ChatMemory.CONVERSATION_ID;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;

class Assistant {

    private static final String SESSION = "main";

    private final ChatClient chat;

    Assistant(ChatClient chat) {
        this.chat = chat;
    }

    String ask(String question) {
        return chat.prompt()
                .user(question)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, "default"))
                .call()
                .content();
    }

    String askAgain(String question) {
        return chat.prompt()
                .user(question)
                .advisors(a -> a.param(CONVERSATION_ID, SESSION))
                .call()
                .content();
    }
}
