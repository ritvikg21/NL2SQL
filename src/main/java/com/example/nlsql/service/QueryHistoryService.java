package com.example.nlsql.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.example.nlsql.entity.QueryHistory;
import com.example.nlsql.repo.QueryHistoryRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class QueryHistoryService {

	private static final int DEFAULT_LIMIT = 20;

	private final QueryHistoryRepository repo;

	/** Save a history entry for the authenticated user */
	public void saveHistory(String userId, String question, String sql, int page, int pageSize, long executionMs) {
		if (!StringUtils.hasText(userId) || !StringUtils.hasText(question)) {
			return; // or throw IllegalArgumentException if preferred
		}

		QueryHistory history = new QueryHistory();
		history.setUserId(userId);
		history.setQuestion(question);
		history.setGeneratedSql(sql);
		history.setPage(page);
		history.setPageSize(pageSize);
		history.setExecutionMs(executionMs);
		history.setCreatedAt(LocalDateTime.now());

		repo.save(history);
	}

	/** USER: only sees their own history */
	@Transactional(readOnly = true)
	public List<QueryHistory> getUserHistory(String userId) {
		Pageable pageable = PageRequest.of(0, DEFAULT_LIMIT);
		return repo.findByUserIdOrderByCreatedAtDesc(userId, pageable);
	}

	/** ADMIN: sees all users’ history */
	@Transactional(readOnly = true)
	public List<QueryHistory> getAllHistory() {
		Pageable pageable = PageRequest.of(0, DEFAULT_LIMIT);
		return repo.findAllByOrderByCreatedAtDesc(pageable);
	}

	@Transactional(readOnly = true)
	public String getQueryById(Long queryId) {
	    return repo.getGeneratedSqlById(queryId);
	}

}
