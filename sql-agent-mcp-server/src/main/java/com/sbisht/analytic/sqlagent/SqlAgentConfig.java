package com.sbisht.analytic.sqlagent;

import org.springframework.ai.chat.client.AdvisorParams;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Configuration
public class SqlAgentConfig {

    @Bean
    ChatClient sqlGenerator(ChatModel chatModel, ResourceLoader resourceLoader) throws IOException {
        var systemPrompt = resourceLoader.getResource("classpath:prompts/sql-generator-system-message.txt");
        var schema = resourceLoader.getResource("classpath:device-reonboardin-events-schema.txt")
                .getContentAsString(StandardCharsets.UTF_8);

        return ChatClient.builder(chatModel)
                .defaultSystem(spec -> spec.text(systemPrompt).param("schema", schema))
                .defaultAdvisors(AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT)
                .build();
    }
}
