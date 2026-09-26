package com.sbisht.analytic.agent.contract;

import com.sbisht.analytic.agent.dto.QueryResult;

public record RagAgentRequest(Mode mode,
                              String question,
                              QueryResult queryResult,
                              int maxChunks,
                              boolean fallbackToFirstChunks) {

    public enum Mode {
        ANSWER,
        RETRIEVE_CONTEXT
    }
}
