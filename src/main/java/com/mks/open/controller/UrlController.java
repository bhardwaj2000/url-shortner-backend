package com.mks.open.controller;

import com.mks.open.dto.UrlRequestDto;
import com.mks.open.dto.UrlResponseDto;
import com.mks.open.service.UrlService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

/**
 * REST controller for URL shortening operations.
 * <p>
 * Handles:
 * <ul>
 *   <li>Creating shortened URLs</li>
 *   <li>Redirecting from short URLs to original URLs</li>
 *   <li>Getting URL analytics</li>
 * </ul>
 * <p>
 * Validation:
 * - Uses Jakarta Validation for input validation
 * - Validates URL format and short code format
 *
 * @author DevTeam
 */
@RestController
@RequestMapping("/api/v1")
@Validated
@Tag(name = "url-shortener", description = "URL Shortener operations")
public class UrlController {

    private static final Logger log = LoggerFactory.getLogger(UrlController.class);

    private final UrlService urlService;

    /**
     * Creates a new UrlController.
     *
     * @param urlService the URL service
     */
    public UrlController(UrlService urlService) {
        this.urlService = urlService;
    }

    /**
     * Creates a shortened URL from the original URL.
     *
     * @param request the URL request DTO containing the original URL
     * @return a URL response DTO with the short code and redirect URL
     * @throws IllegalArgumentException if the URL is invalid
     */
    @PostMapping("/urls")
    @Operation(summary = "Create a shortened URL", description = "Generates a short code for the provided URL")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<UrlResponseDto> createShortUrl(
            @Valid @RequestBody UrlRequestDto request) {

        log.info("Creating short URL for: {}", request.originalUrl());
        UrlResponseDto response = urlService.createShortUrl(request);
        log.info("Created short URL with code: {}", response.shortCode());

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Redirects from a short URL to the original URL.
     * <p>
     * Returns HTTP 302 (Found) with the Location header set to the original URL.
     * Also increments the click count atomically.
     *
     * @param shortCode the short code to redirect
     * @return HTTP 302 redirect response
     */
    @GetMapping("/{shortCode}")
    @Operation(summary = "Redirect to original URL", description = "Redirects from short code to original URL")
    public ResponseEntity<Void> redirect(
            @PathVariable @NotBlank @Pattern(regexp = "^[a-zA-Z0-9]{6,8}$") String shortCode) {

        log.debug("Processing redirect for short code: {}", shortCode);

        String originalUrl = urlService.redirect(shortCode);

        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(originalUrl))
                .build();
    }

    /**
     * Gets analytics for a specific short URL.
     *
     * @param shortCode the short code to get analytics for
     * @return a URL analytics DTO with click count and metadata
     */
    @GetMapping("/urls/{shortCode}")
    @Operation(summary = "Get URL analytics", description = "Returns analytics for a shortened URL")
    public ResponseEntity<com.mks.open.dto.UrlAnalyticsDto> getAnalytics(
            @PathVariable @NotBlank @Pattern(regexp = "^[a-zA-Z0-9]{6,8}$") String shortCode) {

        log.debug("Fetching analytics for short code: {}", shortCode);

        com.mks.open.dto.UrlAnalyticsDto analytics = urlService.getAnalytics(shortCode);
        return ResponseEntity.ok(analytics);
    }
}
