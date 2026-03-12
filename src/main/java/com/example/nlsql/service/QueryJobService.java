package com.example.nlsql.service;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.nlsql.dto.QueryJobMessage;
import com.example.nlsql.entity.QueryJob;
import com.example.nlsql.kafka.QueryJobProducer;
import com.example.nlsql.repo.QueryJobRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class QueryJobService {

	private final QueryJobRepository jobRepo;
	private final QueryJobProducer producer;

	/**
	 * Creates a job and sends it to Kafka.
	 */
	@Transactional
	public String submitJob(String userId, String question, Integer page, Integer pageSize) {

		// 1️⃣ Generate public jobId
		String jobId = UUID.randomUUID().toString();

		// 2️⃣ Persist job in DB
		QueryJob job = new QueryJob();
		job.setJobId(jobId);
		job.setUserId(userId);
		job.setQuestion(question);
		job.setStatus("SUBMITTED");
		job.setCreatedAt(LocalDateTime.now());

		jobRepo.save(job);

		// 3️⃣ Build Kafka message
		QueryJobMessage msg = new QueryJobMessage();
		msg.setJobId(jobId);
		msg.setUserId(userId);
		msg.setQuestion(question);
		msg.setPage(page);
		msg.setPageSize(pageSize);

		// 4️⃣ Send to Kafka
		producer.send(msg);

		return jobId;
	}
}
