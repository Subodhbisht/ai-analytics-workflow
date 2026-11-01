package com.sbisht.analytic.agent.service;

import com.sbisht.analytic.agent.client.AgentUnavailableException;
import com.sbisht.analytic.agent.client.McpAgentClients;
import com.sbisht.analytic.agent.contract.RagAgentRequest;
import com.sbisht.analytic.agent.contract.ResultAnalysisRequest;
import com.sbisht.analytic.agent.contract.SqlAgentRequest;
import com.sbisht.analytic.agent.dto.AnalyticsRequest;
import com.sbisht.analytic.agent.dto.AnalyticsResponse;
import com.sbisht.analytic.agent.dto.QueryResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AnalyticsHandoffOrchestrator {
    private static final Logger log = LoggerFactory.getLogger(AnalyticsHandoffOrchestrator.class);
    private static final int MAX_SQL_ATTEMPTS = 3;
    private final PromptIntentRouter intentRouter;
    private final ConversationQueryContextStore contextStore;
    private final McpAgentClients agents;
    private final SqlGuardrail sqlGuardrail;

    public AnalyticsHandoffOrchestrator(PromptIntentRouter intentRouter,
                                        ConversationQueryContextStore contextStore,
                                        McpAgentClients agents,
                                        SqlGuardrail sqlGuardrail) {
        this.intentRouter = intentRouter;
        this.contextStore = contextStore;
        this.agents = agents;
        this.sqlGuardrail = sqlGuardrail;
    }

    public AnalyticsResponse analyze(AnalyticsRequest request) {
        if (request == null || request.message() == null || request.message().isBlank()) {
            return noChart("Please ask a question so I can analyze it.");
        }
        var message = request.message().strip();
        var execution = WorkflowExecution.start(message);
        var context = contextStore.getLastContext();
        try {
            execution.transitionTo(WorkflowExecutionState.ROUTING, AgentControl.HOST, log);
            var decision = intentRouter.route(message, context);
            var workflow = HandoffWorkflow.from(decision.intent());
            execution.selectWorkflow(workflow, log);
            log.info("Intent decision: {}", decision);
            var response = switch (workflow) {
                case NEW_SQL -> analyzeSql(execution, message, false);
                case SQL_PLUS_RAG -> analyzeSql(execution, message, true);
                case DOC_RAG -> answerFromDocs(execution, message);
                case FOLLOWUP_ON_LAST_RESULT -> context
                        .map(previous -> analyzeFollowUp(execution, message, previous))
                        .orElseGet(() -> requireHuman(execution,
                                "I need a previous SQL result before I can answer that follow-up."));
                case CLARIFY -> requireHuman(execution,
                        "I need a little more detail before I can choose whether to query data or use the previous result.");
            };
            if (!execution.isWaitingForHuman()) execution.complete(log);
            return response;
        } catch (AgentUnavailableException exception) {
            execution.fail(log, exception);
            return noChart("The %s needed to resolve this query is not responding. Please try again shortly."
                    .formatted(exception.agentDisplayName()));
        } catch (RuntimeException exception) {
            execution.fail(log, exception);
            throw exception;
        }
    }

    private AnalyticsResponse analyzeSql(WorkflowExecution execution, String message, boolean includeDocs) {
        execution.transitionTo(WorkflowExecutionState.SQL_GENERATING, AgentControl.SQL_AGENT, log);
        var result = agents.invokeSqlAgent(new SqlAgentRequest(message, MAX_SQL_ATTEMPTS, true));
        if (!result.succeeded() || result.queryResult() == null || result.generation() == null) {
            var error = result.errorMessage() == null || result.errorMessage().isBlank()
                    ? "SQL agent returned no query result" : result.errorMessage();
            throw new AgentUnavailableException("SQL agent", error);
        }
        var validation = sqlGuardrail.validate(result.queryResult().sql());
        if (!validation.valid()) {
            throw new IllegalStateException("SQL agent returned a query rejected by the host guardrail: "
                    + validation.errorMessage());
        }
        var queryResult = result.queryResult();
        contextStore.save(message, queryResult);
        log.info("SQL agent completed rowCount={}", queryResult.rows().size());
        var documentation = includeDocs
                ? retrieveDocs(execution, message, queryResult, 8, true)
                : "";
        return analyzeResult(execution, message, queryResult, documentation, null);
    }

    private AnalyticsResponse analyzeFollowUp(WorkflowExecution execution, String message,
                                               ConversationQueryContext previous) {
        var documentation = retrieveDocs(execution, message, previous.queryResult(), 5, false);
        return analyzeResult(execution, message, previous.queryResult(), documentation, previous.question());
    }

    private AnalyticsResponse answerFromDocs(WorkflowExecution execution, String message) {
        execution.transitionTo(WorkflowExecutionState.RAG_RETRIEVING, AgentControl.RAG_AGENT, log);
        var answer = agents.invokeKnowledgeRagAgent(new RagAgentRequest(
                RagAgentRequest.Mode.ANSWER, message, null, 5, false));
        return noChart(answer.groundedAnswer());
    }

    private String retrieveDocs(WorkflowExecution execution, String message, QueryResult queryResult,
                                int maxChunks, boolean fallbackToFirstChunks) {
        execution.transitionTo(WorkflowExecutionState.RAG_RETRIEVING, AgentControl.RAG_AGENT, log);
        var response = agents.invokeKnowledgeRagAgent(new RagAgentRequest(
                RagAgentRequest.Mode.RETRIEVE_CONTEXT, message, queryResult, maxChunks, fallbackToFirstChunks));
        return response.documentationContext() == null ? "" : response.documentationContext();
    }

    private AnalyticsResponse analyzeResult(WorkflowExecution execution, String message, QueryResult result,
                                            String documentation, String previousQuestion) {
        execution.transitionTo(WorkflowExecutionState.RESULT_ANALYZING, AgentControl.RESULT_ANALYSIS_AGENT, log);
        return agents.invokeResultAnalysisAgent(new ResultAnalysisRequest(
                message, result, documentation, previousQuestion));
    }

    private AnalyticsResponse requireHuman(WorkflowExecution execution, String message) {
        execution.waitingForHuman(log);
        return noChart(message);
    }

    private AnalyticsResponse noChart(String insight) {
        return new AnalyticsResponse(insight, null, null, null);
    }

}
