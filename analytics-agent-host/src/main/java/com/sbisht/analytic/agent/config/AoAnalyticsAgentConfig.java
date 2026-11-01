package com.sbisht.analytic.agent.config;

import org.springframework.ai.chat.client.AdvisorParams;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;

@Configuration
public class AoAnalyticsAgentConfig {

    @Bean(name = "IntentRouter")
    ChatClient intentRouter(@Qualifier("ollamaChatModel") ChatModel ollamaChatModel,
                            ResourceLoader resourceLoader) {
        var systemMessage = resourceLoader.getResource("classpath:prompts/intent-router-system-message.txt");
        return ChatClient.builder(ollamaChatModel)
                .defaultSystem(systemMessage)
                .defaultAdvisors(AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT)
                .build();
    }
}
