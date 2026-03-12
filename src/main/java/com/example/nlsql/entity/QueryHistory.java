package com.example.nlsql.entity;

import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "query_history")
public class QueryHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Always tied to authenticated user
    @Column(nullable = false)
    private String userId;

    @Column(nullable = false)
    private String question;

    @Column(columnDefinition = "TEXT")
    private String generatedSql;

    private LocalDateTime createdAt;

    private int page;
    private int pageSize;

    private long executionMs;
}
