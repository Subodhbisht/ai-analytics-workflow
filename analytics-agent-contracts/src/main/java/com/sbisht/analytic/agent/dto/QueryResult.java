package com.sbisht.analytic.agent.dto;

import java.util.List;
import java.util.Map;

public record QueryResult(String sql,
                          List<Map<String, Object>> rows) {
}
