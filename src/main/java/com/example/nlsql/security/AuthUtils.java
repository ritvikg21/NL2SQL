package com.example.nlsql.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public class AuthUtils {

    // Extract userId from JWT ("sub")
    public static String currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth.getName();  // The subject (sub) of JWT
    }

    // Extract role from JWT ("ROLE_USER" or "ROLE_ADMIN")
    public static String currentRole() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth.getAuthorities().iterator().next().getAuthority();
    }
}
