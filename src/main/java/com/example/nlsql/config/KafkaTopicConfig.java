package com.example.nlsql.config;


import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KafkaTopicConfig {

    /**
     * Topic to store query execution jobs
     * - 1 partition (we can increase later)
     * - replicationFactor = 1 (local dev)
     */
    @Bean
    public NewTopic queryJobsTopic() {
        return new NewTopic("query-jobs", 1, (short) 1);
    }
}
