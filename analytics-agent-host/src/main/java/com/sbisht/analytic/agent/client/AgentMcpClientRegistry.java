package com.sbisht.analytic.agent.client;

import io.modelcontextprotocol.client.McpSyncClient;

import java.util.Map;
import java.util.Optional;

public class AgentMcpClientRegistry implements AutoCloseable {
    private final Map<String, McpSyncClient> clients;

    public AgentMcpClientRegistry(Map<String, McpSyncClient> clients) {
        this.clients = Map.copyOf(clients);
    }

    public Optional<McpSyncClient> find(String name) {
        return Optional.ofNullable(clients.get(name));
    }

    @Override
    public void close() {
        clients.values().forEach(client -> {
            try {
                client.closeGracefully();
            } catch (RuntimeException ignored) {
                // Closing one unavailable agent must not prevent closing the remaining clients.
            }
        });
    }
}
