package com.sbisht.analytic.agent.service;

import com.sbisht.analytic.agent.dto.QueryResult;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

public record ConversationQueryContext(String question,
                                       QueryResult queryResult) {

    public String sql() {
        return queryResult.sql();
    }

    public List<Map<String, @Nullable Object>> sampleRows(int maxRows) {
        return queryResult.rows().stream()
                .limit(maxRows)
                .toList();
    }

    public List<String> columns() {
        if (queryResult.rows().isEmpty()) {
            return List.of();
        }
        return List.copyOf(queryResult.rows().getFirst().keySet());
    }
}
