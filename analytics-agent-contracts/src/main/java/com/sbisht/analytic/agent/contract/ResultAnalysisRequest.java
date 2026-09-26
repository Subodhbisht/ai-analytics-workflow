package com.sbisht.analytic.agent.contract;

import com.sbisht.analytic.agent.dto.QueryResult;

public record ResultAnalysisRequest(String question,
                                    QueryResult queryResult,
                                    String documentationContext,
                                    String previousQuestion) {
}
