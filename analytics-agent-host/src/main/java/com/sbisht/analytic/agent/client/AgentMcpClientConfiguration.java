package com.sbisht.analytic.agent.client;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema.Implementation;
import org.springframework.ai.mcp.client.common.autoconfigure.properties.McpStreamableHttpClientProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.LinkedHashMap;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(McpStreamableHttpClientProperties.class)
public class AgentMcpClientConfiguration {

    @Bean(destroyMethod = "close")
    AgentMcpClientRegistry agentMcpClientRegistry(
            McpStreamableHttpClientProperties properties,
            @Value("${spring.ai.mcp.client.request-timeout:120s}") Duration requestTimeout,
            @Value("${spring.ai.mcp.client.name:analytics-agent-host}") String clientName,
            @Value("${spring.ai.mcp.client.version:0.0.1}") String clientVersion) {
        var clients = new LinkedHashMap<String, io.modelcontextprotocol.client.McpSyncClient>();
        properties.getConnections().forEach((name, parameters) -> {
            var transportBuilder = HttpClientStreamableHttpTransport.builder(parameters.url())
                    .connectTimeout(requestTimeout)
                    .openConnectionOnStartup(false);
            if (parameters.endpoint() != null && !parameters.endpoint().isBlank()) {
                transportBuilder.endpoint(parameters.endpoint());
            }
            var client = McpClient.sync(transportBuilder.build())
                    .requestTimeout(requestTimeout)
                    .initializationTimeout(requestTimeout)
                    .clientInfo(Implementation.builder(clientName + "-" + name, clientVersion).build())
                    .build();
            clients.put(name, client);
        });
        return new AgentMcpClientRegistry(clients);
    }
}
