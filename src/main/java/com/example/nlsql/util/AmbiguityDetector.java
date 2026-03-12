package com.example.nlsql.util;

import com.example.nlsql.dto.ClarificationResponse;

public class AmbiguityDetector {

	public static ClarificationResponse detect(String userQuery, String sql) {

		ClarificationResponse res = new ClarificationResponse();

		if (sql == null || sql.isBlank()) {
			res.needsClarification = true;
			res.question = "I could not generate a valid SQL. Can you clarify your request?";
			return res;
		}

		String lowerSql = sql.toLowerCase();

		// 1) Missing FROM clause
		if (!lowerSql.contains(" from ")) {
			res.needsClarification = true;
			res.question = "Which table do you want to query?";
			return res;
		}

		// 2) Find table name after FROM
		String table = extractTableName(lowerSql);
		if (table == null || table.isBlank()) {
			res.needsClarification = true;
			res.question = "I couldn't detect the table name. Which table should I use?";
			return res;
		}

		// 3) Check if user asked vague things
		if (userQuery.toLowerCase().contains("sales") || userQuery.toLowerCase().contains("revenue")) {
			if (!lowerSql.contains("total") && !lowerSql.contains("amount")) {
				res.needsClarification = true;
				res.question = "Which sales/revenue column should I use?";
				return res;
			}
		}

		res.needsClarification = false;
		return res;
	}

	private static String extractTableName(String sql) {
		try {
			int fromIndex = sql.indexOf(" from ") + 6;
			String afterFrom = sql.substring(fromIndex).trim();
			// stop at WHERE, GROUP BY, ORDER BY, LIMIT
			return afterFrom.split(" ")[0];
		} catch (Exception e) {
			return null;
		}
	}
}
