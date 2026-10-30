package com.acme;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.model.tool.ToolCallingChatOptions;

class ChatSetup {
    Object options() {
        return ToolCallingChatOptions.builder().internalToolExecutionEnabled(false).build();
    }

    Object names(ChatClient chat) {
        return chat.prompt().toolNames("weather").call().content();
    }

    Object memory(ChatMemory memory) {
        return MessageChatMemoryAdvisor.builder(memory).conversationId("a").build();
    }

    String constant() {
        return ChatMemory.DEFAULT_CONVERSATION_ID;
    }

    String json(Object value) {
        return ModelOptionsUtils.toJsonString(value);
    }
}
