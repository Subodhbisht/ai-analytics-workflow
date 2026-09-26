package com.sbisht.analytic.sqlagent;

import com.sbisht.analytic.agent.dto.QueryResult;
import com.sbisht.analytic.agent.dto.SqlGenerationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.regex.Pattern;

@Service
class SqlExecutionPipeline {

    private static final Logger log = LoggerFactory.getLogger(SqlExecutionPipeline.class);
    private static final Pattern COLUMN_NOT_FOUND = Pattern.compile(
            "(?i)column\\s+['\"`]?([^'\"`\\s;]+)['\"`]?\\s+(?:does not exist|not found|unknown)"
    );
    private static final Pattern TABLE_NOT_FOUND = Pattern.compile(
            "(?i)(?:table|relation)\\s+['\"`]?([^'\"`\\s;]+)['\"`]?\\s+(?:does not exist|not found|unknown)"
    );
    private static final Pattern H2_INVALID_ALIAS = Pattern.compile(
            "(?is)\\bAS\\s+\\[\\*]\\s*([A-Za-z_][A-Za-z0-9_]*)\\b.*expected\\s+\"identifier\""
    );

    private final ChatClient sqlGenerator;
    private final JdbcTemplate jdbcTemplate;

    SqlExecutionPipeline(ChatClient sqlGenerator, JdbcTemplate jdbcTemplate) {
        this.sqlGenerator = sqlGenerator;
        this.jdbcTemplate = jdbcTemplate;
    }

    SqlGenerationResult generate(String question) {
        var startedAt = System.nanoTime();
        log.info("SQL generation started questionLength={}", question == null ? 0 : question.length());
        var result = normalize(sqlGenerator.prompt(question).call().entity(SqlGenerationResult.class));
        log.info("SQL generation completed confident={} durationMs={}",
                result.confident(), elapsedMillis(startedAt));
        log.debug("Generated SQL: {}", result.sql());
        return result;
    }

    SqlGenerationResult repair(String question,
                               String previousSql,
                               String previousReasoning,
                               String errorSummary) {
        var startedAt = System.nanoTime();
        log.info("SQL repair started error={}", errorSummary);
        var result = normalize(sqlGenerator.prompt()
                .user("""
                      Original Question:
                      %s

                      Reasoning:
                      %s

                      Generated SQL:
                      %s

                      Database Error:
                      %s

                      Repair the SQL and return a new result.
                      """.formatted(question, previousReasoning, previousSql, errorSummary))
                .call()
                .entity(SqlGenerationResult.class));
        log.info("SQL repair completed confident={} durationMs={}",
                result.confident(), elapsedMillis(startedAt));
        log.debug("Repaired SQL: {}", result.sql());
        return result;
    }

    @Transactional(readOnly = true)
    QueryResult execute(String sql) {
        var startedAt = System.nanoTime();
        var cleanedSql = SqlSafetyValidator.clean(sql).replaceFirst(";\\s*$", "").strip();
        log.info("SQL database execution started");
        var rows = jdbcTemplate.queryForList(cleanedSql);
        log.info("SQL database execution completed rowCount={} durationMs={}",
                rows.size(), elapsedMillis(startedAt));
        return new QueryResult(cleanedSql, rows);
    }

    String databaseErrorGist(Throwable exception) {
        var message = deepestMessage(exception);
        if (message.isBlank()) {
            return exception.getClass().getSimpleName();
        }

        var summary = message.replaceAll("\\s+", " ").strip()
                .replaceFirst("(?i);?\\s*SQL statement:.*$", "")
                .replaceFirst("(?i)\\s*Position:\\s*\\d+.*$", "")
                .strip();

        var invalidAliasMatcher = H2_INVALID_ALIAS.matcher(summary);
        if (invalidAliasMatcher.find()) {
            var alias = invalidAliasMatcher.group(1);
            return "Alias '%s' is not valid in H2; use a non-reserved alias such as '%s'"
                    .formatted(alias, safeAlias(alias));
        }

        var columnMatcher = COLUMN_NOT_FOUND.matcher(summary);
        if (columnMatcher.find()) {
            return "Column '%s' does not exist".formatted(columnMatcher.group(1));
        }

        var tableMatcher = TABLE_NOT_FOUND.matcher(summary);
        if (tableMatcher.find()) {
            return "Table '%s' does not exist".formatted(tableMatcher.group(1));
        }

        return summary.length() <= 240 ? summary : summary.substring(0, 237) + "...";
    }

    private SqlGenerationResult normalize(SqlGenerationResult result) {
        if (result == null || result.sql() == null || result.sql().isBlank()) {
            throw new IllegalStateException("SQL generator did not return a SQL query");
        }
        if (!result.confident()) {
            log.info("SQL generator marked this query as low confidence: {}", result.reasoning());
        }
        return new SqlGenerationResult(
                SqlSafetyValidator.clean(result.sql()).replaceFirst(";\\s*$", "").strip(),
                result.confident(),
                result.reasoning() == null ? "" : result.reasoning()
        );
    }

    private String deepestMessage(Throwable exception) {
        var current = exception;
        var message = "";
        while (current != null) {
            if (current.getMessage() != null && !current.getMessage().isBlank()) {
                message = current.getMessage();
            }
            current = current.getCause();
        }
        return message;
    }

    private String safeAlias(String alias) {
        return switch (alias.toLowerCase(Locale.ROOT)) {
            case "month" -> "order_month";
            case "year" -> "order_year";
            case "day" -> "order_day";
            default -> "query_" + alias;
        };
    }

    private long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}
