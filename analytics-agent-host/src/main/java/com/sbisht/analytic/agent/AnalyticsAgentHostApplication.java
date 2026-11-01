package com.sbisht.analytic.agent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.ai.mcp.client.common.autoconfigure.McpClientAutoConfiguration;

@SpringBootApplication(exclude = McpClientAutoConfiguration.class)
public class AnalyticsAgentHostApplication {

	public static void main(String[] args) {
		SpringApplication.run(AnalyticsAgentHostApplication.class, args);
	}

}
