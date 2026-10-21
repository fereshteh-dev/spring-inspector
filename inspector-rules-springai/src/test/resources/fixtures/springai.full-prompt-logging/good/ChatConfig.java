package com.acme.assistant;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class ChatConfig {

    @Bean
    ChatClient chatClient(ChatClient.Builder builder) {
        SimpleLoggerAdvisor logging = SimpleLoggerAdvisor.builder()
                .requestToString(request -> "prompt with " + request.prompt().getInstructions().size() + " messages")
                .responseToString(response -> "response " + response.getMetadata().getId())
                .build();
        return builder.defaultAdvisors(logging).build();
    }
}
