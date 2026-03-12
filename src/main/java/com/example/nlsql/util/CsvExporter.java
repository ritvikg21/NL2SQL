package com.example.nlsql.util;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CsvExporter {

	/**
	 * Converts the query result list into a CSV stream.
	 */
	public static ByteArrayOutputStream writeCsv(List<Map<String, Object>> rows) throws IOException {

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(out));

		if (rows.isEmpty())
			return out;

		// Write headers
		Set<String> headers = rows.get(0).keySet();
		writer.write(String.join(",", headers));
		writer.newLine();

		// Write rows with escaping
		for (Map<String, Object> row : rows) {
			List<String> values = new ArrayList<>();

			for (String h : headers) {
				Object val = row.get(h);

				// Escape quotes and wrap in quotes
				String safe = val == null ? "" : val.toString().replace("\"", "\"\"");
				values.add("\"" + safe + "\"");
			}

			writer.write(String.join(",", values));
			writer.newLine();
		}

		writer.flush();
		return out;
	}
}
