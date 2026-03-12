package com.example.nlsql.controller;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.nlsql.dto.QueryResponse;
import com.example.nlsql.entity.QueryResult;
import com.example.nlsql.repo.QueryJobRepository;
import com.example.nlsql.repo.QueryResultRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@RestController()
@RequestMapping("/api/query")
@RequiredArgsConstructor
public class QueryResultController {

	private final QueryJobRepository queryJobRepository;
	private final QueryResultRepository queryResultRepository;
	private final ObjectMapper objectMapper = new ObjectMapper();

	@GetMapping("/result/{jobId}")
	public QueryResponse fetchQueryResultByJobId(@PathVariable("jobId") String jobId) {

		// 1> Get status by JobId
		String status = queryJobRepository.findStatusByJobId(jobId);

		QueryResponse response = new QueryResponse();
		response.setStatus(status);

		// 2. If job is still running → return only status
		if ("SUBMITTED".equals(status) || status.equals("QUEUED") || status.equals("RUNNING")) {
			return response;
		}

		// 3. If job failed → return error
		if (status.equals("FAILED")) {
			response.setError(queryJobRepository.findErrorByJobId(jobId));
			return response;
		}

		// 4. If job is DONE → fetch result from existing query result table
		QueryResult result = queryResultRepository.findByJobId(jobId).orElse(null);

		if (result == null) {
			response.setStatus("FAILED");
			response.setError("Result not found for jobId: " + jobId);
			return response;
		}

		try {
			// Parse columns & rows stored in JSON
			response.setExplanation(result.getExplanationJson());

			List<Map<String, Object>> rows = objectMapper.readValue(result.getRowsJson(),
					new TypeReference<List<Map<String, Object>>>() {
					});
			response.setRows(rows);

		} catch (Exception e) {
			response.setStatus("FAILED");
			response.setError("Error parsing result JSON: " + e.getMessage());
			return response;
		}

		return response;

	}

}
