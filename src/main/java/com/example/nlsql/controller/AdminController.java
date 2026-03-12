package com.example.nlsql.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.*;

import com.example.nlsql.service.SqlExecutorService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final SqlExecutorService sqlExecutorService;

    /**
     * Admin-only SQL execution endpoint
     */
    @PostMapping("/execute")
    @PreAuthorize("hasRole('ADMIN')")
    public String executeAdminSql(@RequestBody String sql) {

        sqlExecutorService.executeAdminSql(sql); // this must allow DML/DDL

        return "SQL executed successfully by admin.";
    }
}
