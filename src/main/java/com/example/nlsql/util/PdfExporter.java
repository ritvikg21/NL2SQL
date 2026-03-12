package com.example.nlsql.util;

import com.lowagie.text.*;
import com.lowagie.text.pdf.*;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;

public class PdfExporter {

	/**
	 * Converts query results into a PDF table.
	 */
	public static ByteArrayOutputStream writePdf(List<Map<String, Object>> rows) throws Exception {

		ByteArrayOutputStream out = new ByteArrayOutputStream();

		// Create document
		Document doc = new Document(PageSize.A4.rotate());
		PdfWriter.getInstance(doc, out);

		doc.open();

		// Title
		Font titleFont = new Font(Font.HELVETICA, 16, Font.BOLD);
		Paragraph title = new Paragraph("Query Export Report", titleFont);
		title.setAlignment(Element.ALIGN_CENTER);
		title.setSpacingAfter(20);
		doc.add(title);

		if (rows.isEmpty()) {
			doc.add(new Paragraph("No data available."));
			doc.close();
			return out;
		}

		// Create PDF table with dynamic number of columns
		Map<String, Object> firstRow = rows.get(0);
		int colCount = firstRow.keySet().size();

		PdfPTable table = new PdfPTable(colCount);
		table.setWidthPercentage(100);

		// Header row styling
		Font headerFont = new Font(Font.HELVETICA, 12, Font.BOLD);

		for (String col : firstRow.keySet()) {
			PdfPCell cell = new PdfPCell(new Phrase(col, headerFont));
			cell.setBackgroundColor(Color.LIGHT_GRAY);
			cell.setPadding(5);
			table.addCell(cell);
		}

		// Data rows
		for (Map<String, Object> row : rows) {
			for (String col : firstRow.keySet()) {
				Object val = row.get(col);
				PdfPCell cell = new PdfPCell(new Phrase(val == null ? "" : val.toString()));
				cell.setPadding(5);
				table.addCell(cell);
			}
		}

		doc.add(table);
		doc.close();
		return out;
	}
}
