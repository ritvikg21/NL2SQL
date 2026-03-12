package com.example.nlsql.controller;

import java.util.List;

import org.springframework.web.bind.annotation.*;

import com.example.nlsql.entity.QueryHistory;
import com.example.nlsql.security.AuthUtils;
import com.example.nlsql.service.QueryHistoryService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/history")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class HistoryController {

	private final QueryHistoryService historyService;

	@GetMapping
	public List<QueryHistory> getHistory() {

		String userId = AuthUtils.currentUserId();
		String role = AuthUtils.currentRole();

		if (role.equals("ROLE_ADMIN")) {
			return historyService.getAllHistory(); // Admin sees all
		}

		return historyService.getUserHistory(userId); // User sees only own
	}
}
