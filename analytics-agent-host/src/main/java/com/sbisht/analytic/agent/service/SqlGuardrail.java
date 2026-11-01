package com.sbisht.analytic.agent.service;

import com.sbisht.analytic.agent.dto.ValidationResult;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class SqlGuardrail {
    private static final Pattern READ_ONLY = Pattern.compile("(?is)^\\s*(select|with)\\b.*");
    private static final Pattern UNSAFE = Pattern.compile(
            "(?i)\\b(insert|update|delete|drop|alter|create|truncate|merge|call|grant|revoke)\\b");

    public ValidationResult validate(String sql) {
        var cleaned = cleanSql(sql).replaceFirst(";\\s*$", "").strip();
        if (cleaned.isBlank()) return ValidationResult.invalid("Generated SQL is empty");
        if (cleaned.contains(";")) return ValidationResult.invalid("Multiple SQL statements are not allowed");
        if (!READ_ONLY.matcher(cleaned).matches()) return ValidationResult.invalid("Only SELECT queries are allowed");
        var matcher = UNSAFE.matcher(cleaned);
        if (matcher.find()) return ValidationResult.invalid("Unsafe SQL keyword is not allowed: " + matcher.group().toUpperCase(Locale.ROOT));
        return ValidationResult.valids();
    }

    public String cleanSql(String sql) {
        if (sql == null) return "";
        var cleaned = sql.strip();
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceFirst("(?is)^```(?:sql)?\\s*", "").replaceFirst("(?s)\\s*```$", "");
        }
        return cleaned.strip();
    }
}
