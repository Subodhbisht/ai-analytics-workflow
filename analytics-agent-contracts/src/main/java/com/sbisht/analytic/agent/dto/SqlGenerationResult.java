package com.sbisht.analytic.agent.dto;

public record SqlGenerationResult(String sql,
                                  boolean confident,
                                  String reasoning) {
}
