package com.example.nlsql.kafka;


import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.nlsql.dto.ClarificationResponse;
import com.example.nlsql.dto.QueryJobMessage;
import com.example.nlsql.entity.QueryJob;
import com.example.nlsql.entity.QueryResult;
import com.example.nlsql.repo.QueryJobRepository;
import com.example.nlsql.repo.QueryResultRepository;
import com.example.nlsql.service.ExplainService;
import com.example.nlsql.service.QueryHistoryService;
import com.example.nlsql.service.QueryResultCacheService;
import com.example.nlsql.service.SqlExecutorService;
import com.example.nlsql.service.SqlFromAiService;
import com.example.nlsql.service.SqlSanitizerService;
import com.example.nlsql.service.SqlValidationService;
import com.example.nlsql.util.AmbiguityDetector;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class QueryJobConsumer {

    private final QueryJobRepository jobRepo;
    private final QueryResultRepository resultRepo;

    private final SqlFromAiService sqlFromAiService;
    private final SqlExecutorService sqlExecutorService;
    private final SqlValidationService sqlValidationService;
    private final ExplainService explainService;
    private final SqlSanitizerService sqlSanitizerService;
    private final QueryResultCacheService queryResultCacheService;
    private final QueryHistoryService queryHistoryService;

    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Kafka worker that executes the query job.
     */
    @KafkaListener(topics = "query-jobs", groupId = "query-workers")
    @Transactional
    public void handle(QueryJobMessage msg) {

        long start = System.currentTimeMillis();

        String jobId = msg.getJobId();
        String userId = msg.getUserId();
        String question = msg.getQuestion();

        int page = msg.getPage() == null ? 0 : msg.getPage();
        int pageSize = msg.getPageSize() == null ? 30 : msg.getPageSize();
        int offset = page * pageSize;

        QueryJob job = jobRepo.findByJobId(jobId)
                .orElseThrow(() -> new IllegalStateException("Job not found: " + jobId));

        try {
            // 🔁 Mark job RUNNING
            job.setStatus("RUNNING");
            jobRepo.save(job);

            // 1️⃣ Generate SQL
            String generatedSql = sqlFromAiService.generateSql(question);

            // 1.1️⃣ Ambiguity detection
            ClarificationResponse amb = AmbiguityDetector.detect(question, generatedSql);
            if (amb.needsClarification) {
                job.setStatus("FAILED");
                job.setError("Ambiguous question: " + amb.question);
                jobRepo.save(job);
                return;
            }

            // 2️⃣ Validate SQL
            sqlValidationService.validateSelectOnly(generatedSql);

            // 3️⃣ Ensure LIMIT
            generatedSql = sqlValidationService.ensureLimit(generatedSql, 100);

            // 4️⃣ Sanitize SQL
            SqlSanitizerService.Sanitized sanitized =
                    sqlSanitizerService.sanitizeSelect(generatedSql);

            // 5️⃣ Pagination
            String sanitizedSql = sanitized.sql;
            boolean hasLimit = sanitizedSql.toLowerCase(Locale.ROOT).contains("limit");

            String finalSql;
            List<Object> finalParams = new ArrayList<>(sanitized.params);

            if (!hasLimit) {
                finalSql = sanitizedSql + " LIMIT ? OFFSET ?";
                finalParams.add(pageSize + 1);
                finalParams.add(offset);
            } else {
                finalSql = "SELECT * FROM (" + sanitizedSql + ") AS _paged_sub LIMIT ? OFFSET ?";
                finalParams.add(pageSize + 1);
                finalParams.add(offset);
            }

            // 6️⃣ Cache lookup
            String cacheKey = queryResultCacheService.makeKey(
                    sanitized.sql, sanitized.params, page, pageSize, userId);

            List<Map<String, Object>> rows = queryResultCacheService.get(cacheKey);
            Boolean hasMore = queryResultCacheService.getHasMore(cacheKey);

            if (rows == null || hasMore == null) {
            	System.out.println("Cache missed");
                SqlExecutorService.PagedResult paged =
                        sqlExecutorService.executeWithParamsWithHasMore(
                                finalSql, finalParams, pageSize);

                rows = paged.rows;
                hasMore = paged.hasMore;

                queryResultCacheService.put(cacheKey, rows);
                queryResultCacheService.putHasMore(cacheKey, hasMore);
            }

            // 7️⃣ Explanation
            List<Map<String, Object>> sample =
                    rows.size() > 30 ? rows.subList(0, 30) : rows;

            String explanationJson =
                    explainService.explainSql(generatedSql, sample);

            // 8️⃣ Save history
            long executionMs = System.currentTimeMillis() - start;
            queryHistoryService.saveHistory(
                    userId, question, generatedSql, page, pageSize, executionMs);

            // 9️⃣ Save result
            QueryResult result = new QueryResult();
            result.setJobId(jobId);
            result.setGeneratedSql(generatedSql);
            result.setRowsJson(mapper.writeValueAsString(rows));
            result.setExplanationJson(explanationJson);
            result.setHasMore(hasMore);

            resultRepo.save(result);

            // 🔁 Mark job DONE
            job.setStatus("DONE");
            job.setFinishedAt(LocalDateTime.now());
            jobRepo.save(job);

        } catch (Exception ex) {
            // 🔥 Failure handling
            job.setStatus("FAILED");
            job.setError(ex.getMessage());
            job.setFinishedAt(LocalDateTime.now());
            jobRepo.save(job);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error: " + ex.getMessage(),
        			ex);
        }
    }
}




