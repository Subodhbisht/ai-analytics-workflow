package com.sbisht.analytic.agent.contract;

import com.sbisht.analytic.agent.dto.QueryResult;
import com.sbisht.analytic.agent.dto.SqlGenerationResult;

public record SqlAgentResponse(SqlGenerationResult generation,
                               QueryResult queryResult,
                               String errorMessage) {

    public boolean succeeded() {
        return errorMessage == null || errorMessage.isBlank();
    }
}
