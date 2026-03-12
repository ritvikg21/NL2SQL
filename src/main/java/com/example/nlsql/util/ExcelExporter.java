package com.example.nlsql.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;


public class ExcelExporter {
	/**
	 * Converts the query result list into an Excel XLSX file using Apache POI.
	 */
	public static ByteArrayOutputStream writeExcel(List<Map<String, Object>> rows) throws IOException {

		Workbook wb = new XSSFWorkbook();
		Sheet sheet = wb.createSheet("Data");

		ByteArrayOutputStream out = new ByteArrayOutputStream();

		if (rows.isEmpty()) {
			wb.write(out);
			wb.close();
			return out;
		}

		// Headers (Row 0)
		Row header = sheet.createRow(0);
		Set<String> columns = rows.get(0).keySet();

		int colIndex = 0;
		for (String col : columns) {
			header.createCell(colIndex++).setCellValue(col);
		}

		// Rows 1..n
		int rowIndex = 1;
		for (Map<String, Object> row : rows) {
			Row excelRow = sheet.createRow(rowIndex++);
			int cellIndex = 0;

			for (String col : columns) {
				Object val = row.get(col);
				excelRow.createCell(cellIndex++).setCellValue(val == null ? "" : val.toString());
			}
		}

		// Auto-size columns
		for (int i = 0; i < columns.size(); i++) {
			sheet.autoSizeColumn(i);
		}

		wb.write(out);
		wb.close();
		return out;
	}
}
