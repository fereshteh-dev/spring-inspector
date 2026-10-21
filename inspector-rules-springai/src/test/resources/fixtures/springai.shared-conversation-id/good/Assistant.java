package com.acme.assistant;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;

class Assistant {

    private final ChatClient chat;

    Assistant(ChatClient chat) {
        this.chat = chat;
    }

    String ask(String userId, String chatId, String question) {
        String conversationId = userId + ":" + chatId;
        return chat.prompt()
                .user(question)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId).param("topK", 5))
                .call()
                .content();
    }
}
