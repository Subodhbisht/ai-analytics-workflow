package com.sbisht.analytic.sqlagent;

import com.sbisht.analytic.agent.contract.SqlAgentResponse;
import com.sbisht.analytic.agent.dto.SqlGenerationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

@Service
public class SqlAgentMcpTool {

    private static final Logger log = LoggerFactory.getLogger(SqlAgentMcpTool.class);
    private static final int DEFAULT_SQL_ATTEMPTS = 3;
    private static final int MAX_ALLOWED_SQL_ATTEMPTS = 5;

    private final SqlExecutionPipeline pipeline;

    public SqlAgentMcpTool(SqlExecutionPipeline pipeline) {
        this.pipeline = pipeline;
    }

    @McpTool(
            name = "sql_agent",
            title = "SQL Agent",
            description = "Generate, validate, repair, and execute a read-only analytics query in one call."
    )
    public SqlAgentResponse invoke(
            @McpToolParam(description = "Analytics question to answer using the database.", required = true)
            String question,
            @McpToolParam(description = "Maximum generation/repair attempts permitted by the host.", required = true)
            int maxAttempts,
            @McpToolParam(description = "Must be true; this agent only accepts read-only authorization.", required = true)
            boolean readOnly) {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("SQL agent question is required");
        }
        if (!readOnly) {
            throw new IllegalArgumentException("SQL agent requires explicit read-only authorization");
        }

        var allowedAttempts = maxAttempts > 0
                ? Math.min(maxAttempts, MAX_ALLOWED_SQL_ATTEMPTS)
                : DEFAULT_SQL_ATTEMPTS;
        var startedAt = System.nanoTime();
        log.info("SQL agent workflow started questionLength={} maxAttempts={}",
                question.length(), allowedAttempts);
        SqlGenerationResult generation = null;
        String lastError = null;

        try {
            generation = pipeline.generate(question);
            for (int attempt = 1; attempt <= allowedAttempts; attempt++) {
                log.info("SQL agent validating query attempt={} confident={}", attempt, generation.confident());
                lastError = SqlSafetyValidator.validate(generation.sql());

                if (lastError == null) {
                    try {
                        var queryResult = pipeline.execute(generation.sql());
                        log.info("SQL agent workflow completed attempt={} rowCount={} durationMs={}",
                                attempt, queryResult.rows().size(), elapsedMillis(startedAt));
                        return new SqlAgentResponse(generation, queryResult, null);
                    } catch (DataAccessException exception) {
                        lastError = pipeline.databaseErrorGist(exception);
                        log.warn("SQL execution failed attempt={} error={}", attempt, lastError);
                    }
                } else {
                    log.warn("SQL safety validation failed attempt={} error={}", attempt, lastError);
                }

                if (attempt < allowedAttempts) {
                    log.info("SQL agent repairing query nextAttempt={}", attempt + 1);
                    generation = pipeline.repair(
                            question,
                            generation.sql(),
                            generation.reasoning(),
                            lastError);
                }
            }

            log.warn("SQL agent exhausted attempts attempts={} durationMs={} lastError={}",
                    allowedAttempts, elapsedMillis(startedAt), lastError);
            return new SqlAgentResponse(generation, null, lastError);
        } catch (RuntimeException exception) {
            log.error("SQL agent workflow failed durationMs={} error={}",
                    elapsedMillis(startedAt), exception.getMessage(), exception);
            throw exception;
        }
    }

    private long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}
