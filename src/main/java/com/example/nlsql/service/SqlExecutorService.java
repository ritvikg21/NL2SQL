package com.example.nlsql.service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.sql.DataSource;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.example.nlsql.util.DataMaskingUtils;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

// If your class already has constructor injection, add DataSource there.
// Example: private final DataSource dataSource; (injected via constructor)

@Service
@RequiredArgsConstructor
public class SqlExecutorService {

	private final DataSource dataSource;
	private final SqlValidationService sqlValidationService;
	private final JdbcTemplate jdbcTemplate;

	public static class PagedResult {
		public final List<Map<String, Object>> rows;
		public final boolean hasMore;

		public PagedResult(List<Map<String, Object>> rows, boolean hasMore) {
			this.rows = rows;
			this.hasMore = hasMore;
		}
	}

	/**
	 * Execute a SQL string with ordered params (placeholders '?' in the SQL).
	 * Returns rows as List<Map<String,Object>>.
	 */
	public PagedResult executeWithParamsWithHasMore(String sqlWithPlaceholders, List<Object> params, int pageSize) {
		List<Map<String, Object>> allRows = new ArrayList<>();

		try (Connection conn = dataSource.getConnection();
				PreparedStatement ps = conn.prepareStatement(sqlWithPlaceholders)) {

			// Bind parameters in order
			for (int i = 0; i < params.size(); i++) {
				Object param = params.get(i);
				int idx = i + 1;

				if (param == null) {
					ps.setObject(idx, null);
				} else if (param instanceof java.sql.Date) {
					ps.setDate(idx, (java.sql.Date) param);
				} else if (param instanceof java.sql.Time) {
					ps.setTime(idx, (java.sql.Time) param);
				} else if (param instanceof java.sql.Timestamp) {
					ps.setTimestamp(idx, (java.sql.Timestamp) param);
				} else {
					ps.setObject(idx, param);
				}
			}

			try (ResultSet rs = ps.executeQuery()) {
				var md = rs.getMetaData();
				int cols = md.getColumnCount();

				while (rs.next()) {
					Map<String, Object> row = new LinkedHashMap<>();
					for (int c = 1; c <= cols; c++) {
						
						if (md.getColumnLabel(c).equalsIgnoreCase("email")) {
						    row.put(md.getColumnLabel(c), DataMaskingUtils.maskEmail(rs.getObject(c).toString()));
						} else {
							row.put(md.getColumnLabel(c), rs.getObject(c));
						}
					}
					allRows.add(row);
				}
			}

		} catch (Exception e) {
			throw new RuntimeException("Failed to execute paged SQL: " + e.getMessage(), e);
		}

		// Determine hasMore (we requested pageSize+1 rows)
		boolean hasMore = false;
		List<Map<String, Object>> trimmed;

		if (allRows.size() > pageSize) {
			hasMore = true;
			trimmed = new ArrayList<>(allRows.subList(0, pageSize)); // drop extra row
		} else {
			trimmed = allRows;
		}

		return new PagedResult(trimmed, hasMore);
	}

	/**
	 * ADMIN ONLY Executes DML / DDL statements (INSERT, UPDATE, DELETE, CREATE,
	 * ALTER, DROP)
	 */
	@Transactional
	public int executeAdminSql(String sql) {

		if (sql == null || sql.isBlank()) {
			throw new IllegalArgumentException("SQL must not be empty");
		}

		// Trim and remove trailing semicolon (JDBC does NOT want it)
		String cleanedSql = sql.trim();
		if (cleanedSql.endsWith(";")) {
			cleanedSql = cleanedSql.substring(0, cleanedSql.length() - 1);
		}

		String normalized = cleanedSql.toLowerCase(Locale.ROOT);

		if (!isDmlOrDdl(normalized)) {
			throw new IllegalArgumentException("Only DML / DDL statements are allowed in admin execution");
		}

		// DML → use update()
		if (normalized.startsWith("insert") || normalized.startsWith("update") || normalized.startsWith("delete")) {

			return jdbcTemplate.update(cleanedSql); // returns rows affected
		}

		// DDL → use execute()
		jdbcTemplate.execute(cleanedSql);
		return 0; // DDL has no row count
	}

	private boolean isDmlOrDdl(String sql) {
		return sql.startsWith("insert") || sql.startsWith("update") || sql.startsWith("delete")
				|| sql.startsWith("create") || sql.startsWith("alter") || sql.startsWith("drop")
				|| sql.startsWith("truncate");
	}

}
