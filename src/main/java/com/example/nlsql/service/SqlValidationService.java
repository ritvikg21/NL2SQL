package com.example.nlsql.service;

import org.springframework.stereotype.Service;

/**
 * Service responsible for basic safety checks on the generated SQL
 * before it is executed on the database.
 *
 * NOTE: This is intentionally simple and should be improved for
 * production use with a proper SQL parser or allow-list of patterns.
 */
@Service
public class SqlValidationService {

    /**
     * Validate that the SQL is safe to execute.
     *
     * Rules:
     * - Must start with SELECT (case-insensitive, ignoring leading whitespace)
     * - Must not contain dangerous keywords like DELETE, UPDATE, DROP, etc.
     *
     * @param sql The SQL string returned by the LLM.
     * @throws IllegalArgumentException if the SQL is considered unsafe.
     */
    public void validateSelectOnly(String sql) {
        if (sql == null || sql.isBlank()) {
            throw new IllegalArgumentException("Generated SQL is empty");
        }

        String trimmedUpper = sql.trim().toUpperCase();

        // Only allow SELECT queries
        if (!trimmedUpper.startsWith("SELECT")) {
            throw new IllegalArgumentException("Only SELECT queries are allowed");
        }

        // Very naive checks for dangerous keywords inside the query.
        // In a more advanced version you'd likely parse the SQL
        // or strictly control the prompt to avoid such cases.
        String[] forbiddenKeywords = {
                "DELETE", "UPDATE", "DROP", "ALTER", "INSERT", "TRUNCATE"
        };

        for (String keyword : forbiddenKeywords) {
            if (trimmedUpper.contains(keyword)) {
                throw new IllegalArgumentException("Forbidden keyword detected in SQL: " + keyword);
            }
        }
    }

    /**
     * Optionally enforce a LIMIT clause to avoid returning millions of rows.
     *
     * If the SQL already contains a LIMIT (case-insensitive), it will be returned as-is.
     * Otherwise a default limit is appended.
     *
     * @param sql The originally generated SQL.
     * @param defaultLimit Max number of rows to be returned.
     * @return SQL that is guaranteed to contain a LIMIT clause.
     */
    public String ensureLimit(String sql, int defaultLimit) {
        String upper = sql.toUpperCase();
        if (upper.contains(" LIMIT ")) {
            // Limit already present - do not modify
            return sql;
        }
        // Append LIMIT at the end. We assume the SQL already ends with semicolon or not.
        String trimmed = sql.trim();
        if (trimmed.endsWith(";")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed + " LIMIT " + defaultLimit;
    }
}
