package com.sbisht.analytic.agent.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

public record IntentDecision(AnalyticsIntent intent,
                             boolean needsSql,
                             boolean needsReasonCodeContext,
                             String reasoning) {

    public static IntentDecision newSqlFallback(String reasoning) {
        return new IntentDecision(AnalyticsIntent.NEW_SQL, true, false, reasoning);
    }

    @JsonFormat(with = JsonFormat.Feature.ACCEPT_CASE_INSENSITIVE_VALUES)
    public enum AnalyticsIntent {
        NEW_SQL,
        FOLLOWUP_ON_LAST_RESULT,
        DOC_RAG,
        SQL_PLUS_RAG,
        CLARIFY
    }
}
