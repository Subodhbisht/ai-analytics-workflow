package com.sbisht.analytic.agent.service;

import com.sbisht.analytic.agent.dto.AnalyticsRequest;
import com.sbisht.analytic.agent.dto.AnalyticsResponse;
import org.springframework.stereotype.Service;

@Service
public class AnalyticsService {

    private final AnalyticsHandoffOrchestrator orchestrator;

    public AnalyticsService(AnalyticsHandoffOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    public AnalyticsResponse analyze(AnalyticsRequest request) {
        return orchestrator.analyze(request);
    }
}
