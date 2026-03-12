package com.example.nlsql.service;

import com.example.nlsql.config.OpenAiProperties;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * ExplainService — adapted to call Gemini (Google Generative Language) style endpoint with
 * robust handling for truncated responses (MAX_TOKENS).
 */
@Service
@RequiredArgsConstructor
public class ExplainService {

    private static final Logger log = LoggerFactory.getLogger(ExplainService.class);

    private final RestTemplate restTemplate;
    private final OpenAiProperties properties;

    // Cache explanations by a short hash of the SQL (null-safe)
    @Cacheable(
    	    value = "explanations",
    	    key = "#root.target.safeKey(#root.args[0])"
    	)
    public String explainSql(String sql, List<Map<String, Object>> sampleRows) {
        // Defensive: if sql is null/blank, return fallback result
        if (sql == null || sql.isBlank()) {
            return fallbackExplanation("EMPTY_OR_NULL_SQL");
        }

        // 1) Build system + user prompt
        String systemPrompt = """
        		Return compact JSON only. No commentary, no markdown, no code fences.

        		STRICT RULES:
        		- Base your explanation ONLY on the SQL query text and the provided sample rows.
        		- Do NOT mention or assume columns, filters, or table meanings that do not explicitly appear in the SQL.
        		- Do NOT guess business intent unless it is directly visible in the SQL.
        		- If the SQL selects only one column, describe only that.
        		- If you cannot infer meaningful insights from the SQL, return an empty array for "insights".

        		Output must exactly match this shape:
        		{"summary":"<one short sentence (<=25 words)>","insights":["<insight1>","<insight2>"]}

        		If you cannot infer insights, use:
        		"insights": []
        		""";


        // 2) Prepare sample rows JSON (limit size)
        List<Map<String, Object>> limited = sampleRows == null ? Collections.emptyList()
                : sampleRows.stream().limit(30).map(this::maskRow).collect(Collectors.toList());

        String userPrompt = """
                SQL:
                %s

                Sample rows:
                %s

                Return the JSON object only.
                """.formatted(sql, toJson(limited));

        // === Gemini request / retry policy configuration ===
        final int initialMaxOutput = 800;   // starting budget for model output tokens
        final int maxAllowedOutput = 4000;  // absolute upper cap to prevent runaway cost
        final int MAX_RETRIES = 2;          // number of retries to increase token budget
        final double temperature = 0.0;

        // Build base request body skeleton (contents)
        Map<String, Object> requestBody = new HashMap<>();
        List<Map<String, Object>> parts = List.of(Map.of("text", systemPrompt + "\n\n" + userPrompt));
        requestBody.put("contents", List.of(Map.of("parts", parts)));

        // generationConfig placed under requestBody (camelCase keys)
        Map<String, Object> generationConfig = new HashMap<>();
        generationConfig.put("maxOutputTokens", initialMaxOutput);
        generationConfig.put("temperature", temperature);
        generationConfig.put("candidateCount", 1);
        requestBody.put("generationConfig", generationConfig);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        // Build Gemini URL using configured baseUrl + model + api key (query param)
        String modelName = properties.getModel() == null || properties.getModel().isBlank()
                ? "gemini-2.5-flash" : properties.getModel();
        String url = properties.getBaseUrl() + "/models/" + modelName + ":generateContent?key=" + properties.getApiKey();

        String modelOutput = null;
        String finishReason = null;

        try {
            for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
                // Increase maxOutputTokens exponentially on retries
                int budget = Math.min(initialMaxOutput * (1 << attempt), maxAllowedOutput);
                ((Map<String, Object>) requestBody.get("generationConfig")).put("maxOutputTokens", budget);

                // Recreate entity because requestBody map mutated
                HttpEntity<Map<String, Object>> attemptEntity = new HttpEntity<>(requestBody, headers);

                ResponseEntity<Map> resp = restTemplate.postForEntity(url, attemptEntity, Map.class);
                if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
                    log.warn("LLM returned non-2xx or empty body on attempt {}: status={}", attempt, resp.getStatusCode());
                    return fallbackExplanation(sql);
                }

                @SuppressWarnings("unchecked")
                List<Map<String, Object>> candidates = (List<Map<String, Object>>) resp.getBody().get("candidates");
                if (candidates == null || candidates.isEmpty()) {
                    log.warn("LLM returned no candidates on attempt {}", attempt);
                    return fallbackExplanation(sql);
                }

                Map<String, Object> candidate = candidates.get(0);

                // Extract finishReason if present
                Object fr = candidate.get("finishReason");
                finishReason = fr == null ? null : fr.toString();

                @SuppressWarnings("unchecked")
                Map<String, Object> content = (Map<String, Object>) candidate.get("content");
                if (content == null) {
                    log.warn("LLM candidate has no content on attempt {}", attempt);
                    return fallbackExplanation(sql);
                }

                @SuppressWarnings("unchecked")
                List<Map<String, Object>> responseParts = (List<Map<String, Object>>) content.get("parts");
                if (responseParts == null || responseParts.isEmpty()) {
                    log.warn("LLM content.parts missing on attempt {}", attempt);
                    return fallbackExplanation(sql);
                }

                Object maybeText = responseParts.get(0).get("text");
                if (!(maybeText instanceof String text) || text.isBlank()) {
                    log.warn("LLM returned empty text on attempt {}", attempt);
                    return fallbackExplanation(sql);
                }

                modelOutput = text;
                // Try to extract JSON object from the returned text
                String json = extractJson(modelOutput);
                if (json != null) {
                    // Success — parseable JSON found
                    return json;
                }

                // No full JSON found; if truncated due to max tokens, try again with larger budget
                if ("MAX_TOKENS".equalsIgnoreCase(finishReason)) {
                    log.info("LLM response truncated by MAX_TOKENS on attempt {} (budget={}) — retrying with larger budget", attempt, budget);
                    // continue loop to retry with more budget
                    continue;
                } else {
                    // Not truncated by tokens but still no JSON — break to attempt continuation step
                    log.info("LLM returned incomplete JSON but not MAX_TOKENS (finishReason={})", finishReason);
                    break;
                }
            }

            // If we reach here, modelOutput exists but JSON not complete -> attempt a single continuation call
            if (modelOutput != null) {
                log.info("Attempting continuation prompt to finish truncated output.");
                String continuePrompt = """
                        The previous response was cut off. Continue the previous output and complete the JSON object ONLY.
                        Previous partial output:
                        %s
                        """.formatted(modelOutput);

                Map<String, Object> contBody = new HashMap<>();
                List<Map<String, Object>> contParts = List.of(Map.of("text", continuePrompt));
                contBody.put("contents", List.of(Map.of("parts", contParts)));

                Map<String, Object> contGen = new HashMap<>();
                contGen.put("maxOutputTokens", Math.min(maxAllowedOutput, initialMaxOutput * (1 << MAX_RETRIES)));
                contGen.put("temperature", temperature);
                contGen.put("candidateCount", 1);
                contBody.put("generationConfig", contGen);

                HttpEntity<Map<String, Object>> contEntity = new HttpEntity<>(contBody, headers);
                ResponseEntity<Map> contResp = restTemplate.postForEntity(url, contEntity, Map.class);
                if (contResp.getStatusCode().is2xxSuccessful() && contResp.getBody() != null) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> candidates2 = (List<Map<String, Object>>) contResp.getBody().get("candidates");
                    if (candidates2 != null && !candidates2.isEmpty()) {
                        Map<String, Object> cand2 = candidates2.get(0);
                        @SuppressWarnings("unchecked")
                        Map<String, Object> content2 = (Map<String, Object>) cand2.get("content");
                        if (content2 != null) {
                            @SuppressWarnings("unchecked")
                            List<Map<String, Object>> parts2 = (List<Map<String, Object>>) content2.get("parts");
                            if (parts2 != null && !parts2.isEmpty()) {
                                Object contTextObj = parts2.get(0).get("text");
                                if (contTextObj instanceof String contText && !contText.isBlank()) {
                                    String combined = modelOutput + contText;
                                    String json = extractJson(combined);
                                    if (json != null) return json;
                                }
                            }
                        }
                    }
                }
            }

            // final fallback if we couldn't obtain a complete JSON
            log.warn("Could not obtain complete JSON from LLM after retries/continuation; returning fallback.");
            return fallbackExplanation(sql);

        } catch (Exception e) {
            log.error("Error calling Gemini generateContent", e);
            return fallbackExplanation(sql);
        }
    }

    // Basic helpers (reuse patterns from SqlFromAiService)
    public String safeKey(String sql) {
    	 String normalized = sql
    	            .trim()
    	            .replaceAll("\\s+", " ")
    	            .toLowerCase();
        return Integer.toString(normalized.hashCode());
    }


    private String extractContent(Map<String, Object> choice) {
        // --- Standard: message.content ---
        Object msg = choice.get("message");
        if (msg instanceof Map<?, ?> raw) {

            Object c = raw.get("content");
            if (c instanceof String s && !s.isBlank())
                return s;

            // content as array (some providers)
            if (c instanceof List<?> list && !list.isEmpty()) {
                Object first = list.get(0);
                if (first instanceof Map<?, ?> inner) {
                    Object t = inner.get("text");
                    if (t instanceof String s2 && !s2.isBlank())
                        return s2;
                } else if (first instanceof String s3 && !s3.isBlank()) {
                    return s3;
                }
            }

            // --- FIX: provider puts text inside message.reasoning ---
            Object reasoning = raw.get("reasoning");
            if (reasoning instanceof String r && !r.isBlank())
                return r;

            // some return reasoning_details
            Object rd = raw.get("reasoning_details");
            if (rd != null)
                return rd.toString();

            // message.text or message.output_text
            Object text = raw.get("text");
            if (text instanceof String s4 && !s4.isBlank())
                return s4;

            Object out = raw.get("output_text");
            if (out instanceof String s5 && !s5.isBlank())
                return s5;
        }

        // other locations
        Object t = choice.get("text");
        if (t instanceof String s6 && !s6.isBlank())
            return s6;

        Object out = choice.get("output_text");
        if (out instanceof String s7 && !s7.isBlank())
            return s7;

        // streaming-style delta
        Object delta = choice.get("delta");
        if (delta instanceof Map<?, ?> d) {
            Object content = d.get("content");
            if (content instanceof String s8 && !s8.isBlank())
                return s8;
        }

        return null;
    }

    private String extractJson(String text) {
        // crude: find first '{' and last '}' and return substring
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1).trim();
        }
        return null;
    }

    private String fallbackExplanation(String sql) {
        // Minimal deterministic fallback
        String summary = "This query returns rows selected by the provided SQL.";
        String json = "{\"summary\":\"" + escapeJson(summary) + "\",\"insights\":[]}";
        return json;
    }

    private Map<String, Object> maskRow(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : row.entrySet()) {
            String col = e.getKey().toLowerCase();
            Object val = e.getValue();
            if (val == null) {
                out.put(e.getKey(), null);
                continue;
            }
            // naive masking heuristics: emails, phone, credit-card-like strings
            if (col.contains("email") && val instanceof String s) {
                out.put(e.getKey(), maskEmail(s));
            } else if ((col.contains("phone") || col.contains("mobile")) && val instanceof String s2) {
                out.put(e.getKey(), maskPhone(s2));
            } else {
                out.put(e.getKey(), val);
            }
        }
        return out;
    }

    private String maskEmail(String s) {
        int at = s.indexOf('@');
        if (at <= 1)
            return "****";
        return s.charAt(0) + "****" + s.substring(at);
    }

    private String maskPhone(String s) {
        if (s.length() <= 4)
            return "****";
        return "****" + s.substring(s.length() - 4);
    }

    // Very small JSON serializer for rows (use Jackson if you prefer)
    private String toJson(Object obj) {
        try {
            // If your project already includes Jackson ObjectMapper, use it.
            com.fasterxml.jackson.databind.ObjectMapper m = new com.fasterxml.jackson.databind.ObjectMapper();
            return m.writeValueAsString(obj);
        } catch (Exception ex) {
            return "[]";
        }
    }

    private String escapeJson(String s) {
        return s.replace("\"", "\\\"");
    }
}
