package com.example.nlsql.repo;



import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

import com.example.nlsql.entity.QueryResult;


public interface QueryResultRepository extends JpaRepository<QueryResult, Long> {

    Optional<QueryResult> findByJobId(String jobId);
}
