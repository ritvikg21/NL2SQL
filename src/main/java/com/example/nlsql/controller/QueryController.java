package com.example.nlsql.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.example.nlsql.dto.QueryRequest;
import com.example.nlsql.security.AuthUtils;
import com.example.nlsql.service.QueryJobService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class QueryController {
	private final QueryJobService queryJobService;

	@PostMapping("/query")
	public ResponseEntity<?>  runQuery(@RequestBody QueryRequest request) {
		
		//validate request
		String userId = AuthUtils.currentUserId(); // always from JWT to stop spoofing of userID

		if (request == null || request.getQuestion() == null || request.getQuestion().isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Question must not be empty");
		}

		final int DEFAULT_PAGE = 0;
		final int DEFAULT_PAGE_SIZE = 30;
		final int MAX_PAGE_SIZE = 1000;

		int page = request.getPage() == null ? DEFAULT_PAGE : Math.max(0, request.getPage());
		int pageSize = request.getPageSize() == null ? DEFAULT_PAGE_SIZE : request.getPageSize();
		if (pageSize <= 0)
			pageSize = DEFAULT_PAGE_SIZE;
		if (pageSize > MAX_PAGE_SIZE)
			pageSize = MAX_PAGE_SIZE;
				
		//implementing kafka publish the event
		//submitJob(String userId, String question, Integer page, Integer pageSize) 
		String queryJobId = queryJobService.submitJob(userId, request.getQuestion(), page, pageSize);
		System.out.println(queryJobId);
		
		return ResponseEntity.ok(Map.of("jobId", queryJobId, "status", "SUBMITTED"));

	}
}
