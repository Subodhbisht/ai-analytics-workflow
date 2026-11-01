package com.sbisht.analytic.agent.client;

public class AgentUnavailableException extends RuntimeException {
    private final String agentDisplayName;

    public AgentUnavailableException(String agentName, Throwable cause) {
        super(agentName + " is unavailable", cause);
        this.agentDisplayName = agentName;
    }

    public AgentUnavailableException(String message) {
        super(message);
        this.agentDisplayName = message;
    }

    public AgentUnavailableException(String agentDisplayName, String message) {
        super(message);
        this.agentDisplayName = agentDisplayName;
    }

    public String agentDisplayName() {
        return agentDisplayName;
    }
}
