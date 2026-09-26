package com.sbisht.analytic.agent.config;

import com.google.genai.Client;
import org.springframework.ai.chat.client.AdvisorParams;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.model.google.genai.autoconfigure.chat.GoogleGenAiChatProperties;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Configuration
public class AoAnalyticsAgentConfig {

    @Bean(name = "SqlGenerator")
    ChatClient sqlGenerator(@Qualifier("ollamaChatModel") ChatModel ollamaChatModel,
                            ResourceLoader resourceLoader) throws IOException {
        var sqlGeneratorSystemPrompt = resourceLoader.getResource("classpath:prompts/sql-generator-system-message.txt");
        var resource = resourceLoader.getResource("classpath:/device-reonboardin-events-schema.txt");
        var reonboardingSchema = resource.getContentAsString(StandardCharsets.UTF_8);

        return ChatClient.builder(ollamaChatModel)
                .defaultSystem(spec -> spec.text(sqlGeneratorSystemPrompt).param("schema", reonboardingSchema))
                .defaultAdvisors(AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT)
                .build();
    }

    @Bean(name = "IntentRouter")
    ChatClient intentRouter(@Qualifier("ollamaChatModel") ChatModel ollamaChatModel,
                            ResourceLoader resourceLoader) {
        var systemMessage = resourceLoader.getResource("classpath:prompts/intent-router-system-message.txt");

        return ChatClient.builder(ollamaChatModel)
                .defaultSystem(systemMessage)
                .defaultAdvisors(AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT)
                .build();
    }

    @Bean(destroyMethod = "close")
    Client summarizerGoogleGenAiClient(@Value("${spring.ai.google.genai.api-key}") String apiKey) {
        var builder = Client.builder();
        if (StringUtils.hasText(apiKey)) {
            builder.apiKey(apiKey);
        }
        return builder.build();
    }

    @Bean(name = "SummarizerGoogleChatModel")
    GoogleGenAiChatModel summarizerGoogleChatModel(Client summarizerGoogleGenAiClient,
                                                   ToolCallingManager toolCallingManager,
                                                   @Value("${spring.ai.google.genai.chat.options.model}") String model) {
        var chatProperties = new GoogleGenAiChatProperties();
        chatProperties.setModel(model);

        return GoogleGenAiChatModel.builder()
                .genAiClient(summarizerGoogleGenAiClient)
                .options(chatProperties.toOptions())
                .toolCallingManager(toolCallingManager)
                .build();
    }

    @Bean(name = "Summarizer")
    public ChatClient summarizerChatClient(@Qualifier("SummarizerGoogleChatModel") ChatModel summarizerGoogleChatModel,
                                           ResourceLoader resourceLoader) {
        var systemMessage = resourceLoader.getResource("classpath:prompts/summarizer-system-message.txt");
        var chatMemory = MessageWindowChatMemory.builder().maxMessages(5).build();
        var chatMemoryAdvisor = MessageChatMemoryAdvisor.builder(chatMemory).build();

        return ChatClient.builder(summarizerGoogleChatModel).defaultSystem(systemMessage)
                .defaultAdvisors(AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT.andThen(spec ->
                        spec.advisors(chatMemoryAdvisor).param(ChatMemory.CONVERSATION_ID, "default")) // intentional
                ).build();
    }

}
