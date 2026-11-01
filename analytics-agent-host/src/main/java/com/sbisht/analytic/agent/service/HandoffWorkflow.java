package com.sbisht.analytic.agent.service;

import com.sbisht.analytic.agent.dto.IntentDecision.AnalyticsIntent;

public enum HandoffWorkflow {
    NEW_SQL, DOC_RAG, SQL_PLUS_RAG, FOLLOWUP_ON_LAST_RESULT, CLARIFY;

    static HandoffWorkflow from(AnalyticsIntent intent) {
        return valueOf(intent.name());
    }
}
