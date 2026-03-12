package com.example.nlsql.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * This object is sent to Kafka.
 * It must contain everything needed
 * to execute the job later.
 */
@Getter
@Setter
public class QueryJobMessage {

    private String jobId;
    private String userId;
    private String question;
    private Integer page;
    private Integer pageSize;
}
