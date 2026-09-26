package com.sbisht.analytic.ragagent;

import org.springframework.ai.chat.client.AdvisorParams;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;

@Configuration
public class RagAgentConfig {

    @Bean
    ChatClient summarizerChatClient(ChatModel chatModel, ResourceLoader resourceLoader) {
        var systemMessage = resourceLoader.getResource("classpath:prompts/summarizer-system-message.txt");
        var chatMemory = MessageWindowChatMemory.builder().maxMessages(5).build();
        var chatMemoryAdvisor = MessageChatMemoryAdvisor.builder(chatMemory).build();

        return ChatClient.builder(chatModel)
                .defaultSystem(systemMessage)
                .defaultAdvisors(AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT.andThen(spec ->
                        spec.advisors(chatMemoryAdvisor).param(ChatMemory.CONVERSATION_ID, "default")))
                .build();
    }
}
