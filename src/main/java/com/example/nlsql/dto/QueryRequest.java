package com.example.nlsql.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Represents the JSON request body sent from the frontend.
 *
 * Example:
 * {
 *   "question": "Show me all users created last week"
 * }
 */
@Getter
@Setter
@NoArgsConstructor
public class QueryRequest {

    /**
     * The natural language question typed by the user in the UI.
     */
    private String question;
    private Integer page;     // 0-based page index (optional)
    private Integer pageSize; // desired page size (optional)
    private String userId;
}



