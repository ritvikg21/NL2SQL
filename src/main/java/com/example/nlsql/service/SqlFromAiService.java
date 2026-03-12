package com.example.nlsql.service;

import com.example.nlsql.config.OpenAiProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class SqlFromAiService {

	private final RestTemplate restTemplate;
	private final OpenAiProperties properties;
	private final SchemaService schemaService;

	public String generateSql(String question) {
	    // 1) Build dynamic schema description (you already had this)
	    String schemaDesc = schemaService.getPromptDescription();
	    System.out.println(schemaDesc);

	    String systemPrompt = """
	            You are an assistant that writes ONLY PostgreSQL SELECT queries.

	            Rules:
	            - Use ONLY the tables described in the schema.
	            - Never modify data (no INSERT, UPDATE, DELETE, DROP, ALTER, TRUNCATE).
	            - Return ONLY the SQL query, without any explanation, markdown, or backticks.
	            - Prefer simple, easy to understand SQL.
	            - If unsure, return: SELECT 1;

	            Schema:
	            %s
	            """.formatted(schemaDesc);

	    // build the combined prompt text (Gemini prefers contents/parts)
	    String combined = systemPrompt + "\n\nUser: " + question;

	    // === Build Gemini-style request body ===
	    Map<String, Object> requestBody = new HashMap<>();
	    List<Map<String, Object>> parts = List.of(Map.of("text", combined));
	    requestBody.put("contents", List.of(Map.of("parts", parts)));

	    // nested generationConfig (camelCase keys)
	    Map<String, Object> generationConfig = new HashMap<>();
	    generationConfig.put("maxOutputTokens", 512); // tune as needed
	    generationConfig.put("temperature", 0.0);
	    generationConfig.put("candidateCount", 1);
	    requestBody.put("generationConfig", generationConfig);

	    HttpHeaders headers = new HttpHeaders();
	    headers.setContentType(MediaType.APPLICATION_JSON);
	    // DO NOT set bearer auth if using ?key= in URL
	    HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

	    // Build URL using model available from ListModels (you have gemini-2.5-flash)
	    String modelName = properties.getModel() == null || properties.getModel().isBlank()
	            ? "gemini-2.5-flash" : properties.getModel();
	    String url = properties.getBaseUrl()
	            + "/models/" + modelName + ":generateContent?key=" + properties.getApiKey();

	    ResponseEntity<Map> response;
	    try {
	        response = restTemplate.postForEntity(url, entity, Map.class);
	    } catch (Exception e) {
	        System.out.println("LLM call error: " + e.getMessage());
	        return "SELECT 1";
	    }

	    if (!response.getStatusCode().is2xxSuccessful()) {
	        System.out.println("LLM HTTP error: " + response.getStatusCode());
	        return "SELECT 1";
	    }

	    Map<String, Object> body = response.getBody();
	    if (body == null) {
	        System.out.println("LLM response body is null");
	        return "SELECT 1";
	    }

	    System.out.println("LLM raw response: " + body);

	    // === Gemini extraction (candidates -> content -> parts -> text) ===
	    @SuppressWarnings("unchecked")
	    List<Map<String, Object>> candidates = (List<Map<String, Object>>) body.get("candidates");
	    if (candidates == null || candidates.isEmpty()) {
	        System.out.println("No LLM candidates");
	        return "SELECT 1";
	    }

	    Map<String, Object> firstCandidate = candidates.get(0);

	    // try to read finish reason if present (optional)
	    Object finishReasonObj = firstCandidate.get("finishReason");
	    if (finishReasonObj != null) {
	        System.out.println("LLM finishReason: " + finishReasonObj);
	    }

	    @SuppressWarnings("unchecked")
	    Map<String, Object> content = (Map<String, Object>) firstCandidate.get("content");
	    if (content == null) {
	        System.out.println("LLM candidate content is null");
	        return "SELECT 1";
	    }

	    @SuppressWarnings("unchecked")
	    List<Map<String, Object>> responseParts = (List<Map<String, Object>>) content.get("parts");
	    if (responseParts == null || responseParts.isEmpty()) {
	        System.out.println("LLM content.parts missing");
	        return "SELECT 1";
	    }

	    Object maybeText = responseParts.get(0).get("text");
	    if (!(maybeText instanceof String text) || text.isBlank()) {
	        System.out.println("LLM returned empty text");
	        return "SELECT 1";
	    }

	    // now 'text' contains the LLM output — try to extract an SQL statement from it
	    String sql = extractSql(text);
	    if (sql == null || sql.isBlank()) {
	        // fallback: sometimes the model puts SQL in a reasoning field — try that
	        String reasoning = extractReasoning(firstCandidate);
	        String extracted = extractSql(reasoning);
	        if (extracted != null && !extracted.isBlank()) {
	            return cleanSql(extracted);
	        }
	        System.out.println("SQL not found in model text. Raw: " + text);
	        return "SELECT 1";
	    }

	    return cleanSql(sql);
	}


	// ===================== helpers =====================

	private String extractContent(Map<String, Object> choice) {
		// 1) Try standard Chat Completion content
		Object msg = choice.get("message");
		if (msg instanceof Map<?, ?> raw) {

			// content (normal OpenAI)
			Object c = raw.get("content");
			if (c instanceof String s && !s.isBlank())
				return s;

			// some providers use "output_text"
			Object out = raw.get("output_text");
			if (out instanceof String s2 && !s2.isBlank())
				return s2;

			// some use "text"
			Object text = raw.get("text");
			if (text instanceof String s3 && !s3.isBlank())
				return s3;
		}

		// 2) Legacy field from some providers
		Object text = choice.get("text");
		if (text instanceof String s4 && !s4.isBlank())
			return s4;

		// 3) Nothing found → return null
		return null;
	}

	
	@SuppressWarnings("unchecked")
	private String extractReasoning(Map<String, Object> choice) {
		Object msg = choice.get("message");
		if (msg instanceof Map<?, ?> raw) {
			Object r = ((Map<?, ?>) raw).get("reasoning");
			if (r instanceof String s)
				return s;
			if (r != null)
				return r.toString();
		}
		Object rd = choice.get("reasoning_details");
		if (rd != null)
			return rd.toString();
		return null;
	}

	private String extractSql(String text) {
		if (text == null)
			return null;

		String cleaned = text.replace("```sql", "").replace("```", "").trim();

		Pattern p = Pattern.compile("(?is)\\bSELECT\\b.+?;");
		Matcher m = p.matcher(cleaned);
		if (m.find())
			return m.group();

		if (cleaned.toUpperCase().startsWith("SELECT"))
			return cleaned;

		return null;
	}
	
	
	private String cleanSql(String sql) {
	    if (sql == null) return "SELECT 1";

	    // Remove ALL semicolons anywhere in the string
	    sql = sql.replace(";", "");

	    // Remove extra whitespace
	    return sql.trim();
	}

}
