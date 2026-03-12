package com.example.nlsql.controller;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;

import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.nlsql.service.QueryHistoryService;
import com.example.nlsql.service.SqlExecutorService;
import com.example.nlsql.util.CsvExporter;
import com.example.nlsql.util.ExcelExporter;
import com.example.nlsql.util.PdfExporter;


@RestController
@RequestMapping("/api")
public class ExportController {

	private final QueryHistoryService historyService;
	private final SqlExecutorService sqlExecutorService;

	public ExportController(QueryHistoryService historyService, SqlExecutorService sqlExecutorService) {
		this.historyService = historyService;
		this.sqlExecutorService = sqlExecutorService;
	}

	/**
	 * Unified Export Endpoint: /api/export?queryId=123&format=csv
	 * /api/export?queryId=123&format=excel
	 */
	@GetMapping("/export")
	public ResponseEntity<Resource> export(@RequestParam("queryId") Long queryId, @RequestParam("format") String format)
			throws Exception {

		// 1. Load SQL from history
		String sql = historyService.getQueryById(queryId);

		// 2. Execute SQL (same as UI preview)
		// safety
		if (!sql.trim().toLowerCase().startsWith("select")) {
			throw new IllegalArgumentException("Only SELECT queries can be exported");
		}

		// 2. Execute SQL (same as UI preview)
		SqlExecutorService.PagedResult pagedResult = sqlExecutorService.executeWithParamsWithHasMore(sql, List.of(),
				Integer.MAX_VALUE);
		List<Map<String, Object>> results = pagedResult.rows;
			

		ByteArrayOutputStream out;
		String fileName;
		String contentType;

		// 3. Decide based on "format" param
		switch (format.toLowerCase()) {

		case "csv":
			out = CsvExporter.writeCsv(results);
			fileName = "export.csv";
			contentType = "text/csv";
			break;

		case "excel":
		case "xlsx":
			out = ExcelExporter.writeExcel(results);
			fileName = "export.xlsx";
			contentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
			break;
			
		case "pdf":
		    out = PdfExporter.writePdf(results);
		    fileName = "export.pdf";
		    contentType = "application/pdf";
		    break;


		default:
			throw new IllegalArgumentException("Invalid format. Use 'csv' or 'excel'.");
		}

		// 4. Prepare downloadable file
		InputStreamResource resource = new InputStreamResource(new ByteArrayInputStream(out.toByteArray()));

		return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
				.contentType(MediaType.parseMediaType(contentType)).body(resource);
	}
}
