package com.example.nlsql.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "query_result")
@Getter
@Setter
public class QueryResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** One-to-one with QueryJob */
    @Column(nullable = false, unique = true)
    private String jobId;

    @Column(columnDefinition = "TEXT")
    private String generatedSql;

    /** Result rows stored as JSON */
    @Column(columnDefinition = "TEXT")
    private String rowsJson;

    @Column(columnDefinition = "TEXT")
    private String explanationJson;

    private Boolean hasMore;
}
