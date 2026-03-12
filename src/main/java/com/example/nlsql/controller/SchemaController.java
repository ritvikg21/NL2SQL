package com.example.nlsql.controller;

import java.sql.SQLException;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.nlsql.service.SchemaService;

@RestController
@RequestMapping("/api/schema")
public class SchemaController {

	private final SchemaService schemaService;

	public SchemaController(SchemaService schemaService) {
		this.schemaService = schemaService;
	}

	@GetMapping("/full")
	public ResponseEntity<?> getFullSchema() throws SQLException {
		return ResponseEntity.ok(schemaService.getDatabaseSchema());
	}

	@GetMapping("/prompt")
	public ResponseEntity<?> getPromptSchema() {
		return ResponseEntity.ok(schemaService.getPromptDescription());
	}
}
