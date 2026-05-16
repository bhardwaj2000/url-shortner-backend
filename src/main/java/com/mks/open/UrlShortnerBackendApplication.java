package com.mks.open;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;

/**
 * Main application entry point for the URL Shortener Backend.
 * <p>
 * This application provides a production-ready URL shortening service with:
 * <ul>
 *   <li>RESTful API for URL shortening and redirection</li>
 *   <li>MongoDB-backed storage with unique constraints</li>
 *   <li>Thread-safe short code generation</li>
 *   <li>Atomic click count updates</li>
 *   <li>Analytics and metrics</li>
 * </ul>
 *
 * @author DevTeam
 */
@SpringBootApplication
@EnableRetry
public class UrlShortnerBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(UrlShortnerBackendApplication.class, args);
    }
}
