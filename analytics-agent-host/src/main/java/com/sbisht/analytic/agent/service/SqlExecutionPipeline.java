package com.sbisht.analytic.agent.service;

import com.sbisht.analytic.agent.dto.QueryResult;
import com.sbisht.analytic.agent.dto.SqlGenerationResult;
import com.sbisht.analytic.agent.dto.ValidationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

@Service
public class SqlExecutionPipeline {

    private static final Logger log = LoggerFactory.getLogger(SqlExecutionPipeline.class);
    private static final int MAX_ATTEMPTS = 3;
    private static final Pattern UNSAFE_SQL_KEYWORDS = Pattern.compile(
            "\\b(insert|update|delete|drop|alter|create|truncate|merge|call|grant|revoke)\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern COLUMN_NOT_FOUND = Pattern.compile(
            "(?i)column\\s+['\"`]?([^'\"`\\s;]+)['\"`]?\\s+(?:does not exist|not found|unknown)"
    );
    private static final Pattern TABLE_NOT_FOUND = Pattern.compile(
            "(?i)(?:table|relation)\\s+['\"`]?([^'\"`\\s;]+)['\"`]?\\s+(?:does not exist|not found|unknown)"
    );
    private static final Pattern H2_INVALID_ALIAS = Pattern.compile(
            "(?is)\\bAS\\s+\\[\\*]\\s*([A-Za-z_][A-Za-z0-9_]*)\\b.*expected\\s+\"identifier\""
    );
    private static final Pattern READ_ONLY_QUERY = Pattern.compile("(?is)^\\s*(select|with)\\b.*");

    private final ChatClient sqlGenerator;
    private final JdbcTemplate jdbcTemplate;

    public SqlExecutionPipeline(@Qualifier("SqlGenerator") ChatClient sqlGenerator, JdbcTemplate jdbcTemplate) {
        this.sqlGenerator = sqlGenerator;
        this.jdbcTemplate = jdbcTemplate;
    }

    public QueryResult run(String question) {
        var generation = generate(question);
        var sql = generation.sql();
        log.info("Reasoning for SQL {} and confidence on SQL {} ", generation.reasoning(), generation.confident());
        log.info("Generated SQL {}", sql);
        String lastError = null;

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            var validation = validate(sql);
            if (!validation.valid()) {
                lastError = validation.errorMessage();
                log.warn("Generated SQL failed validation on attempt {}: {}", attempt, lastError);
            } else {
                try {
                    return execute(sql);
                } catch (DataAccessException e) {
                    lastError = databaseErrorGist(e);
                    log.warn("Generated SQL failed on attempt {}. error={}, sql={}", attempt, lastError, sql);
                }
            }

            if (attempt < MAX_ATTEMPTS) {
                generation = repair(question, generation, lastError);
                sql = generation.sql();
            }
        }

        throw new IllegalStateException("Unable to generate valid SQL after %d attempts. Last error: %s"
                .formatted(MAX_ATTEMPTS, lastError));
    }

    SqlGenerationResult generate(String question) {
        return normalize(this.sqlGenerator.prompt(question)
//                .user("""
//                      Original Question:
//                      %s
//                      """.formatted(question))
                .call()
                .entity(SqlGenerationResult.class));
    }

    ValidationResult validate(String sql) {
        log.info("Validating the SQL");
        var cleanedSql = cleanSql(sql);
        if (cleanedSql.isBlank()) {
            return ValidationResult.invalid("Generated SQL is empty");
        }

        var sqlWithoutTrailingSemicolon = cleanedSql.replaceFirst(";\\s*$", "").strip();
        if (sqlWithoutTrailingSemicolon.contains(";")) {
            return ValidationResult.invalid("Multiple SQL statements are not allowed");
        }

        if (!READ_ONLY_QUERY.matcher(sqlWithoutTrailingSemicolon).matches()) {
            return ValidationResult.invalid("Only SELECT queries are allowed");
        }

        var unsafeKeyword = UNSAFE_SQL_KEYWORDS.matcher(sqlWithoutTrailingSemicolon);
        if (unsafeKeyword.find()) {
            return ValidationResult.invalid("Unsafe SQL keyword is not allowed: " + unsafeKeyword.group(1).toUpperCase(Locale.ROOT));
        }

        return ValidationResult.valids();
    }

    @Transactional
    QueryResult execute(String sql) {
        var cleanedSql = cleanSql(sql).replaceFirst(";\\s*$", "").strip();
        log.info("About to execute the generated SQL");

        return new QueryResult(cleanedSql, this.jdbcTemplate.queryForList(cleanedSql));
    }

    SqlGenerationResult repair(String question, SqlGenerationResult previousGeneration, String errorSummary) {
        return normalize(this.sqlGenerator.prompt()
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
                      """.formatted(
                        question,
                        previousGeneration.reasoning(),
                        previousGeneration.sql(),
                        errorSummary))
                .call()
                .entity(SqlGenerationResult.class));
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

        return truncate(summary, 240);
    }

    private SqlGenerationResult normalize(SqlGenerationResult result) {
        System.out.println();
        if (result == null || result.sql() == null || result.sql().isBlank()) {
            throw new IllegalStateException("SQL generator did not return a SQL query");
        }

        if (!result.confident()) {
            log.info("SQL generator marked this query as low confidence: {}", result.reasoning());
        }

        return new SqlGenerationResult(
                cleanSql(result.sql()).replaceFirst(";\\s*$", "").strip(),
                result.confident(),
                result.reasoning() == null ? "" : result.reasoning()
        );
    }

    private String cleanSql(String sql) {
        if (sql == null) {
            return "";
        }
        var cleanedSql = sql.strip();
        if (cleanedSql.startsWith("```")) {
            cleanedSql = cleanedSql.replaceFirst("(?is)^```(?:sql)?\\s*", "")
                                   .replaceFirst("(?s)\\s*```$", "");
        }
        return cleanedSql.strip();
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

    private String truncate(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength - 3) + "...";
    }

    private String safeAlias(String alias) {
        return switch (alias.toLowerCase(Locale.ROOT)) {
            case "month" -> "order_month";
            case "year" -> "order_year";
            case "day" -> "order_day";
            default -> "query_" + alias;
        };
    }

}
