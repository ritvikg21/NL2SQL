package com.example.nlsql.kafka;


import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import com.example.nlsql.dto.QueryJobMessage;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class QueryJobProducer {

	private final KafkaTemplate<String, QueryJobMessage> kafkaTemplate;

	/**
	 * Send job to Kafka.
	 * 
	 * KEY = userId This guarantees ordering per user.
	 */
	public void send(QueryJobMessage job) {
		kafkaTemplate.send("query-jobs", job.getUserId(), // 🔑 Kafka key → same user = same partition
				job);
	}
}
