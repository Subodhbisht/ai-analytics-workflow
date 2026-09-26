package com.sbisht.analytic.agent.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.util.List;

public record AnalyticsResponse(String insight,
                                String chartTitle,
                                ChartType chartType,
                                DataContainer resultData) {

    @JsonFormat(with = JsonFormat.Feature.ACCEPT_CASE_INSENSITIVE_VALUES)
    public enum ChartType {
        BAR, LINE, PIE, DOUGHNUT
    }

    public record DataContainer(List<String> keys,
                                List<Series> values) {
    }

    public record Series(String name,
                         List<Number> points) {
    }
}
