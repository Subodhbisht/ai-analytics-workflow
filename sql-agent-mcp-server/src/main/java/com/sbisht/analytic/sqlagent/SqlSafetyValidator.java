package com.sbisht.analytic.sqlagent;

import java.util.Locale;
import java.util.regex.Pattern;

final class SqlSafetyValidator {

    private static final Pattern UNSAFE_SQL_KEYWORDS = Pattern.compile(
            "\\b(insert|update|delete|drop|alter|create|truncate|merge|call|grant|revoke)\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern READ_ONLY_QUERY = Pattern.compile("(?is)^\\s*(select|with)\\b.*");

    private SqlSafetyValidator() {
    }

    static String validate(String sql) {
        var cleanedSql = clean(sql);
        if (cleanedSql.isBlank()) {
            return "Generated SQL is empty";
        }

        var statement = cleanedSql.replaceFirst(";\\s*$", "").strip();
        if (statement.contains(";")) {
            return "Multiple SQL statements are not allowed";
        }
        if (!READ_ONLY_QUERY.matcher(statement).matches()) {
            return "Only SELECT queries are allowed";
        }

        var unsafeKeyword = UNSAFE_SQL_KEYWORDS.matcher(statement);
        if (unsafeKeyword.find()) {
            return "Unsafe SQL keyword is not allowed: " + unsafeKeyword.group(1).toUpperCase(Locale.ROOT);
        }
        return null;
    }

    static String clean(String sql) {
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
}
