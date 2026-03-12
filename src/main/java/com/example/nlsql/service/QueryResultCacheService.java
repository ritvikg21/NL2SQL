package com.example.nlsql.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
public class QueryResultCacheService {

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final Duration ttl = Duration.ofMinutes(5); // tune this

    public QueryResultCacheService(StringRedisTemplate redis, ObjectMapper mapper) {
        this.redis = redis;
        this.mapper = mapper;
    }

    /** Build stable cache key */
    public String makeKey(String sanitizedSql, List<Object> params, int page, int pageSize, String tenant) throws Exception {
        String base = sanitizedSql + "|" 
                + mapper.writeValueAsString(params) + "|" 
                + page + "|" + pageSize + "|" 
                + (tenant == null ? "" : tenant);

        return "qr:" + Integer.toHexString(base.hashCode());
    }

    /** Get paged result rows */
    public List<Map<String,Object>> get(String key) {
        try {
            String v = redis.opsForValue().get(key);
            if (v == null) return null;
            return mapper.readValue(v, new TypeReference<List<Map<String,Object>>>() {});
        } catch (Exception e) {
            return null; // best-effort
        }
    }

    /** Store paged result rows */
    public void put(String key, List<Map<String,Object>> rows) {
        try {
            String v = mapper.writeValueAsString(rows);
            redis.opsForValue().set(key, v, ttl);
        } catch (Exception ignored) {}
    }

    /** -------------------------
      HAS-MORE Flag Helpers
      ------------------------- */

    public Boolean getHasMore(String key) {
        try {
            String v = redis.opsForValue().get(key + ":hm");
            if (v == null) return null;
            return "1".equals(v);
        } catch (Exception ignored) {
            return null;
        }
    }

    public void putHasMore(String key, boolean hasMore) {
        try {
            redis.opsForValue().set(key + ":hm", hasMore ? "1" : "0", ttl);
        } catch (Exception ignored) {}
    }

    /** Optional convenience: evict both rows and hasMore */
    public void evict(String key) {
        try { 
            redis.delete(key);
            redis.delete(key + ":hm");
        } catch (Exception ignored) {}
    }
}
