package com.acme;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;

class ChatSetup {
    Object call(ChatClient chat, ChatMemory memory, String id, Object tools) {
        return chat.prompt()
                .tools(tools)
                .advisors(MessageChatMemoryAdvisor.builder(memory).build())
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, id))
                .call()
                .content();
    }

    String ownConversationId(Session session) {
        return session.conversationId("a");
    }

    interface Session {
        String conversationId(String prefix);
    }
}
