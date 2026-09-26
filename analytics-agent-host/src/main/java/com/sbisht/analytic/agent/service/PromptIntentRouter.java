package com.sbisht.analytic.agent.service;

import com.sbisht.analytic.agent.dto.IntentDecision;
import com.sbisht.analytic.agent.dto.IntentDecision.AnalyticsIntent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class PromptIntentRouter {

    private static final Logger log = LoggerFactory.getLogger(PromptIntentRouter.class);

    private final ChatClient intentRouterChatClient;

    public PromptIntentRouter(@Qualifier("IntentRouter") ChatClient intentRouterChatClient) {
        this.intentRouterChatClient = intentRouterChatClient;
    }

    public IntentDecision route(String userPrompt, Optional<ConversationQueryContext> lastContext) {
        try {
            var decision = intentRouterChatClient.prompt()
                    .user("""
                          User Prompt:
                          %s

                          Last SQL Result Available:
                          %s

                          Previous User Question:
                          %s

                          Previous SQL:
                          %s

                          Previous Result Columns:
                          %s

                          Previous Result Sample:
                          %s
                          """.formatted(
                            userPrompt,
                            lastContext.isPresent(),
                            lastContext.map(ConversationQueryContext::question).orElse("none"),
                            lastContext.map(ConversationQueryContext::sql).orElse("none"),
                            lastContext.map(ConversationQueryContext::columns).orElseGet(java.util.List::of),
                            lastContext.map(context -> context.sampleRows(5)).orElseGet(java.util.List::of)))
                    .call()
                    .entity(IntentDecision.class);

            return normalize(decision, lastContext);
        } catch (RuntimeException e) {
            log.warn("Intent routing failed. Falling back to SQL generation.", e);
            return IntentDecision.newSqlFallback("Intent router failed, so the request was treated as a new SQL query.");
        }
    }

    private IntentDecision normalize(IntentDecision decision, Optional<ConversationQueryContext> lastContext) {
        if (decision == null || decision.intent() == null) {
            return IntentDecision.newSqlFallback("Intent router returned an empty decision.");
        }

        if (decision.intent() == AnalyticsIntent.FOLLOWUP_ON_LAST_RESULT && lastContext.isEmpty()) {
            return new IntentDecision(AnalyticsIntent.CLARIFY, false, false,
                    "The prompt looks like a follow-up, but there is no previous SQL result available.");
        }

        return switch (decision.intent()) {
            case NEW_SQL -> new IntentDecision(AnalyticsIntent.NEW_SQL, true, false, decision.reasoning());
            case FOLLOWUP_ON_LAST_RESULT -> new IntentDecision(AnalyticsIntent.FOLLOWUP_ON_LAST_RESULT, false, false, decision.reasoning());
            case DOC_RAG -> new IntentDecision(AnalyticsIntent.DOC_RAG, false, true, decision.reasoning());
            case SQL_PLUS_RAG -> new IntentDecision(AnalyticsIntent.SQL_PLUS_RAG, true, true, decision.reasoning());
            case CLARIFY -> new IntentDecision(AnalyticsIntent.CLARIFY, false, false, decision.reasoning());
        };
    }
}
