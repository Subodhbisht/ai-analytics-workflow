package com.sbisht.analytic.agent.contract;

import java.util.List;

public record RagAgentResponse(String groundedAnswer,
                               String documentationContext,
                               List<RagDocument> documents) {

    public record RagDocument(String reasonCode, String content) {
    }
}
