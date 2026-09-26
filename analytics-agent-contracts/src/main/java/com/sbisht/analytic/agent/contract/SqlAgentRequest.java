package com.sbisht.analytic.agent.contract;

public record SqlAgentRequest(String question,
                              int maxAttempts,
                              boolean readOnly) {
}
