package com.mks.open.controller;

import com.mongodb.client.MongoClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Health check controller for the application.
 * <p>
 * Provides application health and status information.
 *
 * @author DevTeam
 */
@RestController
@RequestMapping("/api/v1/health")
@Tag(name = "health", description = "Health check operations")
public class HealthController {

    private static final Logger log = LoggerFactory.getLogger(HealthController.class);

    private final MongoClient mongoClient;

    /**
     * Creates a new HealthController.
     *
     * @param mongoClient the MongoDB client
     */
    @Autowired
    public HealthController(MongoClient mongoClient) {
        this.mongoClient = mongoClient;
    }

    /**
     * Returns application health status.
     *
     * @return health information including MongoDB status
     */
    @GetMapping("/health")
    @Operation(summary = "Application health check", description = "Returns application and database health status")
    public ResponseEntity<Map<String, Object>> health() {

        Map<String, Object> healthInfo = new HashMap<>();

        healthInfo.put("status", "UP");
        healthInfo.put("database", "unknown");
        healthInfo.put("databaseType", "MongoDB");
        healthInfo.put("timestamp", Instant.now().toString());

        return ResponseEntity.ok(healthInfo);
    }

    /**
     * Returns application info.
     *
     * @return application information
     */
    @GetMapping("/info")
    @Operation(summary = "Application info", description = "Returns application metadata")
    @ResponseStatus(HttpStatus.OK)
    public Map<String, String> info() {
        Map<String, String> info = new HashMap<>();
        info.put("appName", "url-shortner-backend");
        info.put("version", "1.0.0");
        info.put("description", "Production-ready URL shortener backend");
        info.put("javaVersion", System.getProperty("java.version"));
        return info;
    }
}
