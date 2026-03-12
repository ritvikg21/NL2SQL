package com.example.nlsql.entity;


import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "query_job")
@Getter
@Setter
public class QueryJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Public identifier returned to frontend */
    @Column(nullable = false, unique = true)
    private String jobId;

    /** User from JWT */
    @Column(nullable = false)
    private String userId;

    /** Original natural language question */
    @Column(nullable = false, length = 2000)
    private String question;

    /**
     * Job lifecycle:
     * SUBMITTED → RUNNING → DONE / FAILED
     */
    @Column(nullable = false)
    private String status;

    /** Optional error message if FAILED */
    @Column(length = 2000)
    private String error;

    private LocalDateTime createdAt;
    private LocalDateTime finishedAt;
}
