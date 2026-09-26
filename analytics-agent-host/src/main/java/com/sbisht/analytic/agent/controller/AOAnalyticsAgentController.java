package com.sbisht.analytic.agent.controller;

import com.sbisht.analytic.agent.dto.AnalyticsRequest;
import com.sbisht.analytic.agent.dto.AnalyticsResponse;
import com.sbisht.analytic.agent.service.AnalyticsService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/analytics")
public class AOAnalyticsAgentController {

//    private final ChatClient chatClient;

    private final AnalyticsService analyticsService;


    public AOAnalyticsAgentController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @PostMapping
    public AnalyticsResponse analyze(@RequestBody AnalyticsRequest request) {
        return analyticsService.analyze(request);
    }

}
