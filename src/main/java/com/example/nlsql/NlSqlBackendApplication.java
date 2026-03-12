package com.example.nlsql;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
/**
 * Main Spring Boot application class.
 *
 * This is the entry point of the backend. When you run this class,
 * it starts an embedded Tomcat server and exposes REST endpoints.
 */
@SpringBootApplication
@EnableCaching
public class NlSqlBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(NlSqlBackendApplication.class, args);
    }
    
}
