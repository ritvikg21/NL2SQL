package com.example.nlsql.repo;


import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.nlsql.entity.QueryJob;

public interface QueryJobRepository extends JpaRepository<QueryJob, Long> {

	Optional<QueryJob> findByJobId(String jobId);
	
	@Query("SELECT q.status FROM QueryJob q WHERE q.jobId = :jobId")
	String findStatusByJobId(@Param("jobId") String jobId);


    @Query("SELECT q.error FROM QueryJob q where q.jobId = :jobId")
	String findErrorByJobId(@Param("jobId") String jobId);
}
