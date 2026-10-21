package com.acme.assistant;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.PromptChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;

class ChatSetup {

    ChatClient chatClient(ChatClient.Builder builder, ChatMemory memory) {
        return builder.defaultAdvisors(PromptChatMemoryAdvisor.builder(memory).build()).build();
    }
}
