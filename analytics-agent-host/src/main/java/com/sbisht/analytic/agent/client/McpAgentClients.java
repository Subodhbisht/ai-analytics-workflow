package com.sbisht.analytic.agent.client;

import com.sbisht.analytic.agent.contract.RagAgentRequest;
import com.sbisht.analytic.agent.contract.RagAgentResponse;
import com.sbisht.analytic.agent.contract.ResultAnalysisRequest;
import com.sbisht.analytic.agent.contract.SqlAgentRequest;
import com.sbisht.analytic.agent.contract.SqlAgentResponse;
import com.sbisht.analytic.agent.dto.AnalyticsResponse;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.util.JsonHelper;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Component
public class McpAgentClients {
    private static final Logger log = LoggerFactory.getLogger(McpAgentClients.class);
    private static final String SQL_SERVER = "sql-agent";
    private static final String RAG_SERVER = "knowledge-rag-agent";
    private static final String RESULT_ANALYSIS_SERVER = "result-analysis-agent";

    private final AgentMcpClientRegistry clients;
    private final JsonHelper jsonHelper = new JsonHelper();

    public McpAgentClients(AgentMcpClientRegistry clients) {
        this.clients = clients;
    }

    public SqlAgentResponse invokeSqlAgent(SqlAgentRequest request) {
        return call(SQL_SERVER, "SQL agent", "sql_agent", request, SqlAgentResponse.class);
    }

    public RagAgentResponse invokeKnowledgeRagAgent(RagAgentRequest request) {
        return call(RAG_SERVER, "Knowledge/RAG agent", "knowledge_rag_agent", request, RagAgentResponse.class);
    }

    public AnalyticsResponse invokeResultAnalysisAgent(ResultAnalysisRequest request) {
        return call(RESULT_ANALYSIS_SERVER, "Result analysis agent", "result_analysis_agent", request,
                AnalyticsResponse.class);
    }

    private <T> T call(String serverName, String agentDisplayName, String toolName,
                       Object request, Class<T> responseType) {
        var invocationId = UUID.randomUUID();
        var startedAt = System.nanoTime();
        try {
            var arguments = jsonHelper.convertToMap(request);
            arguments.values().removeIf(Objects::isNull);
            log.info("MCP handoff started invocationId={} server={} tool={} argumentNames={}",
                    invocationId, serverName, toolName, arguments.keySet());
            var client = clientFor(serverName);
            var result = client.callTool(CallToolRequest.builder(toolName).arguments(arguments).build());
            if (Boolean.TRUE.equals(result.isError())) {
                throw new IllegalStateException("MCP tool %s failed: %s".formatted(toolName, textContent(result.content())));
            }

            T response;
            if (result.structuredContent() instanceof Map<?, ?> structuredContent) {
                response = jsonHelper.convertFromMap(stringKeyedMap(structuredContent), responseType);
            } else {
                var json = textContent(result.content());
                if (json.isBlank()) throw new IllegalStateException("MCP tool %s returned no result".formatted(toolName));
                response = jsonHelper.fromJson(json, responseType);
            }
            if (response == null) throw new IllegalStateException("MCP tool %s returned no response".formatted(toolName));
            log.info("MCP handoff completed invocationId={} server={} tool={} durationMs={}",
                    invocationId, serverName, toolName, elapsedMillis(startedAt));
            return response;
        } catch (AgentUnavailableException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            log.warn("MCP handoff failed invocationId={} server={} tool={} durationMs={} error={}",
                    invocationId, serverName, toolName, elapsedMillis(startedAt), exception.getMessage(), exception);
            throw new AgentUnavailableException(agentDisplayName, exception);
        }
    }

    private McpSyncClient clientFor(String serverName) {
        var client = clients.find(serverName).orElseThrow(() -> new IllegalStateException(
                "MCP connection '%s' is not configured.".formatted(serverName)));
        initializeIfNeeded(serverName, client);
        return client;
    }

    private void initializeIfNeeded(String expectedServerName, McpSyncClient client) {
        if (client.isInitialized()) return;
        synchronized (client) {
            if (!client.isInitialized()) {
                log.info("Initializing MCP connection server={}", expectedServerName);
                client.initialize();
                var serverInfo = client.getServerInfo();
                if (serverInfo == null || !expectedServerName.equals(serverInfo.name())) {
                    throw new IllegalStateException("Expected MCP server '%s' but connected to '%s'"
                            .formatted(expectedServerName, serverInfo == null ? "unknown" : serverInfo.name()));
                }
                log.info("MCP connection initialized server={} version={}", serverInfo.name(), serverInfo.version());
            }
        }
    }

    private Map<String, Object> stringKeyedMap(Map<?, ?> source) {
        var result = new LinkedHashMap<String, Object>();
        source.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    private String textContent(List<?> content) {
        return content.stream().filter(TextContent.class::isInstance).map(TextContent.class::cast)
                .map(TextContent::text).findFirst().orElse("");
    }

    private long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}
