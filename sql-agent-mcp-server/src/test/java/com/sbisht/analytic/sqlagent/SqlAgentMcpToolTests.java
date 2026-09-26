package com.sbisht.analytic.sqlagent;

import com.sbisht.analytic.agent.dto.QueryResult;
import com.sbisht.analytic.agent.dto.SqlGenerationResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SqlAgentMcpToolTests {

    @Test
    void generatesAndExecutesInOneToolInvocation() {
        var generation = new SqlGenerationResult(
                "SELECT count(*) AS total FROM onboarding_events",
                true,
                "count rows");
        var queryResult = new QueryResult(generation.sql(), List.of(Map.of("total", 3)));
        var pipeline = new RecordingPipeline(generation, queryResult);
        var tool = new SqlAgentMcpTool(pipeline);

        var response = tool.invoke("Count onboarding events", 3, true);

        assertThat(response.queryResult()).isEqualTo(queryResult);
        assertThat(response.errorMessage()).isNull();
        assertThat(pipeline.generateCalls).isEqualTo(1);
        assertThat(pipeline.executeCalls).isEqualTo(1);
    }

    @Test
    void repairsUnsafeSqlInsideTheSameToolInvocation() {
        var unsafe = new SqlGenerationResult("DELETE FROM onboarding_events", false, "unsafe query");
        var repaired = new SqlGenerationResult(
                "SELECT count(*) AS total FROM onboarding_events",
                true,
                "count rows");
        var queryResult = new QueryResult(repaired.sql(), List.of(Map.of("total", 3)));
        var pipeline = new RepairingPipeline(unsafe, repaired, queryResult);
        var tool = new SqlAgentMcpTool(pipeline);

        var response = tool.invoke("Count onboarding events", 3, true);

        assertThat(response.queryResult()).isEqualTo(queryResult);
        assertThat(pipeline.generateCalls).isEqualTo(1);
        assertThat(pipeline.repairCalls).isEqualTo(1);
        assertThat(pipeline.executeCalls).isEqualTo(1);
    }

    @Test
    void rejectsRequestsWithoutReadOnlyAuthorization() {
        var pipeline = new RecordingPipeline(
                new SqlGenerationResult("SELECT 1", true, "constant"),
                new QueryResult("SELECT 1", List.of(Map.of("value", 1))));
        var tool = new SqlAgentMcpTool(pipeline);

        assertThatThrownBy(() -> tool.invoke("Return one", 3, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("SQL agent requires explicit read-only authorization");
        assertThat(pipeline.generateCalls).isZero();
        assertThat(pipeline.executeCalls).isZero();
    }

    private static final class RecordingPipeline extends SqlExecutionPipeline {

        private final SqlGenerationResult generation;
        private final QueryResult queryResult;
        private int generateCalls;
        private int executeCalls;

        private RecordingPipeline(SqlGenerationResult generation, QueryResult queryResult) {
            super(null, null);
            this.generation = generation;
            this.queryResult = queryResult;
        }

        @Override
        SqlGenerationResult generate(String question) {
            generateCalls++;
            return generation;
        }

        @Override
        QueryResult execute(String sql) {
            executeCalls++;
            return queryResult;
        }
    }

    private static final class RepairingPipeline extends SqlExecutionPipeline {

        private final SqlGenerationResult initialGeneration;
        private final SqlGenerationResult repairedGeneration;
        private final QueryResult queryResult;
        private int generateCalls;
        private int repairCalls;
        private int executeCalls;

        private RepairingPipeline(SqlGenerationResult initialGeneration,
                                  SqlGenerationResult repairedGeneration,
                                  QueryResult queryResult) {
            super(null, null);
            this.initialGeneration = initialGeneration;
            this.repairedGeneration = repairedGeneration;
            this.queryResult = queryResult;
        }

        @Override
        SqlGenerationResult generate(String question) {
            generateCalls++;
            return initialGeneration;
        }

        @Override
        SqlGenerationResult repair(String question,
                                   String previousSql,
                                   String previousReasoning,
                                   String errorSummary) {
            repairCalls++;
            return repairedGeneration;
        }

        @Override
        QueryResult execute(String sql) {
            executeCalls++;
            return queryResult;
        }
    }
}
