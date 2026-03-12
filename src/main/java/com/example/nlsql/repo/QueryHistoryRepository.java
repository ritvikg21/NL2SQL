package com.example.nlsql.repo;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.nlsql.entity.QueryHistory;

public interface QueryHistoryRepository extends JpaRepository<QueryHistory, Long> {

	// Normal user history
	List<QueryHistory> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

	// Admin can view everything
	List<QueryHistory> findAllByOrderByCreatedAtDesc(Pageable pageable);

	//get sql from user id
	@Query("select q.generatedSql from QueryHistory q where q.id = :queryId")
    String getGeneratedSqlById(@Param("queryId") Long queryId);

}
