package com.sbisht.analytic.ragagent;

import com.sbisht.analytic.agent.contract.RagAgentRequest;
import com.sbisht.analytic.agent.contract.RagAgentResponse;
import com.sbisht.analytic.agent.dto.QueryResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeRagAgentMcpTool {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeRagAgentMcpTool.class);

    private final ChatClient summarizerChatClient;
    private final ReasonCodeKnowledgeBase knowledgeBase;

    public KnowledgeRagAgentMcpTool(ChatClient summarizerChatClient,
                                    ReasonCodeKnowledgeBase knowledgeBase) {
        this.summarizerChatClient = summarizerChatClient;
        this.knowledgeBase = knowledgeBase;
    }

    @McpTool(
            name = "knowledge_rag_agent",
            title = "Knowledge RAG Agent",
            description = "Retrieve onboarding reason-code documents or return a grounded documentation answer."
    )
    public RagAgentResponse invoke(
            @McpToolParam(description = "ANSWER or RETRIEVE_CONTEXT.", required = true)
            RagAgentRequest.Mode mode,
            @McpToolParam(description = "Question used for retrieval and grounding.", required = true)
            String question,
            @McpToolParam(description = "Optional SQL result used to find reason codes.", required = false)
            QueryResult queryResult,
            @McpToolParam(description = "Maximum number of documents to return.", required = true)
            int maxChunks,
            @McpToolParam(description = "Use initial documents when retrieval has no match.", required = true)
            boolean fallbackToFirstChunks) {
        if (mode == null || question == null) {
            throw new IllegalArgumentException("RAG mode and question are required");
        }

        var startedAt = System.nanoTime();
        try {
            maxChunks = maxChunks > 0 ? maxChunks : 5;
            log.info("RAG agent workflow started mode={} maxChunks={} hasSqlResult={} fallbackEnabled={}",
                    mode, maxChunks, queryResult != null, fallbackToFirstChunks);
            var chunks = knowledgeBase.retrieve(
                    question,
                    queryResult,
                    maxChunks,
                    fallbackToFirstChunks);
            var formattedDocuments = knowledgeBase.format(chunks);
            var documents = chunks.stream()
                    .map(chunk -> new RagAgentResponse.RagDocument(chunk.reasonCode(), chunk.content()))
                    .toList();
            log.info("RAG retrieval completed mode={} documentCount={}", mode, documents.size());

            if (mode == RagAgentRequest.Mode.RETRIEVE_CONTEXT) {
                var section = chunks.isEmpty() ? "" : """

                        Retrieved Onboarding Reason-Code Documentation:

                        %s
                        """.formatted(formattedDocuments);
                log.info("RAG agent workflow completed mode={} durationMs={}", mode, elapsedMillis(startedAt));
                return new RagAgentResponse(null, section, documents);
            }

            log.info("RAG grounded answer generation started documentCount={}", documents.size());
            var insight = summarizerChatClient.prompt().user("""
                  User Question:
                  %s

                  Retrieved Onboarding Reason-Code Documentation:

                  %s

                  Answer using only the retrieved documentation. Return concise Markdown in the insight field.
                  """.formatted(question, formattedDocuments))
                    .call()
                    .entity(TextInsight.class);
            log.info("RAG agent workflow completed mode={} documentCount={} durationMs={}",
                    mode, documents.size(), elapsedMillis(startedAt));
            return new RagAgentResponse(insight.insight(), formattedDocuments, documents);
        } catch (RuntimeException exception) {
            log.error("RAG agent workflow failed mode={} durationMs={} error={}",
                    mode, elapsedMillis(startedAt), exception.getMessage(), exception);
            throw exception;
        }
    }

    private long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    private record TextInsight(String insight) {
    }
}
