package com.sbisht.analytic.resultagent;

import com.sbisht.analytic.agent.contract.ResultAnalysisRequest;
import com.sbisht.analytic.agent.dto.AnalyticsResponse;
import com.sbisht.analytic.agent.dto.QueryResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;

@Service
public class ResultAnalysisAgentMcpTool {

    private static final Logger log = LoggerFactory.getLogger(ResultAnalysisAgentMcpTool.class);

    private final ChatClient summarizerChatClient;

    public ResultAnalysisAgentMcpTool(ChatClient summarizerChatClient) {
        this.summarizerChatClient = summarizerChatClient;
    }

    @McpTool(
            name = "result_analysis_agent",
            title = "Result Analysis Agent",
            description = "Interpret a structured SQL result and produce insights plus optional chart-ready data."
    )
    public AnalyticsResponse invoke(
            @McpToolParam(description = "Current user question or follow-up.", required = true)
            String question,
            @McpToolParam(description = "Executed SQL and structured rows.", required = true)
            QueryResult queryResult,
            @McpToolParam(description = "Optional grounded documentation from the RAG agent.", required = false)
            String documentationContext,
            @McpToolParam(description = "Previous question when analyzing a follow-up.", required = false)
            String previousQuestion) {
        if (question == null || queryResult == null) {
            throw new IllegalArgumentException("Question and SQL result are required");
        }

        var startedAt = System.nanoTime();
        var request = new ResultAnalysisRequest(question, queryResult, documentationContext, previousQuestion);
        var followUp = request.previousQuestion() != null && !request.previousQuestion().isBlank();
        try {
            log.info("Result analysis started rowCount={} followUp={} hasDocumentationContext={}",
                    queryResult.rows().size(), followUp,
                    documentationContext != null && !documentationContext.isBlank());
            var response = followUp ? analyzeFollowUp(request) : analyzeCurrentResult(request);
            log.info("Result analysis completed followUp={} chartType={} durationMs={}",
                    followUp, response.chartType(), elapsedMillis(startedAt));
            return response;
        } catch (RuntimeException exception) {
            log.error("Result analysis failed followUp={} durationMs={} error={}",
                    followUp, elapsedMillis(startedAt), exception.getMessage(), exception);
            throw exception;
        }
    }

    private AnalyticsResponse analyzeCurrentResult(ResultAnalysisRequest request) {
        return summarizerChatClient.prompt().user("""
              User Question: %s

              Executed SQL:

              %s

              SQL Result:

              %s
              %s
              """.formatted(
                        request.question(),
                        request.queryResult().sql(),
                        request.queryResult().rows(),
                        context(request.documentationContext())))
                .call()
                .entity(AnalyticsResponse.class);
    }

    private AnalyticsResponse analyzeFollowUp(ResultAnalysisRequest request) {
        return summarizerChatClient.prompt().user("""
              User Follow-up:
              %s

              Previous User Question:
              %s

              Previous Executed SQL:

              %s

              Previous SQL Result:

              %s
              %s
              """.formatted(
                        request.question(),
                        request.previousQuestion(),
                        request.queryResult().sql(),
                        request.queryResult().rows(),
                        context(request.documentationContext())))
                .call()
                .entity(AnalyticsResponse.class);
    }

    private String context(String documentationContext) {
        return documentationContext == null ? "" : documentationContext;
    }

    private long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}
