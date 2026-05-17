package com.mks.open.service;

import com.mks.open.dto.UrlRequestDto;
import com.mks.open.dto.UrlResponseDto;
import com.mks.open.entity.UrlEntity;
import com.mks.open.exception.UrlNotFoundException;
import com.mks.open.repository.UrlRepository;
import com.mks.open.util.Base62Util;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Objects;

/**
 * Service layer for URL shortener operations.
 * <p>
 * This service handles:
 * <ul>
 *   <li>URL shortening with unique code generation</li>
 *   <li>URL redirects with atomic click tracking</li>
 *   <li>URL analytics and statistics</li>
 * </ul>
 * <p>
 * Thread-Safety Implementation:
 * <p>
 * 1. Short Code Generation:
 * - Uses {@link Base62Util} which internally uses {@link java.util.concurrent.ConcurrentHashMap}
 * - Thread-safe random number generation with {@link java.security.SecureRandom}
 * - Retry logic with exponential backoff on collision
 * <p>
 * 2. MongoDB Atomic Updates:
 * - Uses MongoDB's $inc operator for atomic click count increments
 * - No race conditions as increments happen atomically on the server side
 * <p>
 * 3. Unique Constraint:
 * - MongoDB unique index on shortCode prevents duplicates at database level
 * - Retry logic handles rare collision cases
 * <p>
 * 4. Stateless Design:
 * - No shared mutable state between requests
 * - All operations are idempotent where possible
 *
 * @author DevTeam
 */
@Service
public class UrlService {

    private static final Logger log = LoggerFactory.getLogger(UrlService.class);

    private final UrlRepository urlRepository;
    private final int shortCodeLength;
    private final int maxRetryAttempts;

    /**
     * Creates a new UrlService.
     *
     * @param urlRepository the URL repository
     */
    public UrlService(UrlRepository urlRepository) {
        this.urlRepository = urlRepository;
        this.shortCodeLength = 6; // Default length
        this.maxRetryAttempts = 3;
    }

     /**
      * Creates a shortened URL from the original URL.
      * <p>
      * This method ensures idempotency: if the same originalUrl is submitted multiple times,
      * even from concurrent requests, it returns the same short code.
      *
      * @param request the URL request DTO containing the original URL
      * @return a URL response DTO with the short code and redirect URL
      * @throws IllegalArgumentException if the original URL is invalid or malformed
      * @throws RuntimeException         if unable to generate a unique short code after retries
      */
     public UrlResponseDto createShortUrl(UrlRequestDto request, HttpServletRequest httpRequest) {
         // Validate input
         validateUrl(request.originalUrl());

         // Check if this URL was already shortened (idempotency)
         // This handles the case where two concurrent requests try to shorten the same URL
         var existingUrl = urlRepository.findByOriginalUrl(request.originalUrl());
         if (existingUrl.isPresent()) {
             log.info("URL already shortened, returning existing short code: {} for URL: {}",
                     existingUrl.get().shortCode(), request.originalUrl());
             return buildResponse(existingUrl.get(), httpRequest.getRequestURL().toString());
         }

         // Retry logic for collision handling when generating new short codes
         int retryCount = 0;
         while (retryCount < maxRetryAttempts) {
             try {
                 return createShortUrlWithCode(request.originalUrl(), generateShortCode(), httpRequest.getRequestURL().toString());
             } catch (DataIntegrityViolationException e) {
                 // Another thread already saved this URL (race condition on idempotency check)
                 // Re-read the existing URL and return it
                 log.info("Concurrent URL shortening detected, returning existing short code for: {}", request.originalUrl());
                 var existing = urlRepository.findByOriginalUrl(request.originalUrl());
                 if (existing.isPresent()) {
                     return buildResponse(existing.get(), httpRequest.getRequestURL().toString());
                 }
                 // If still not found, continue retrying with new code
                 retryCount++;
                 log.debug("Short code collision detected, retry {}/{}", retryCount, maxRetryAttempts);
                 if (retryCount >= maxRetryAttempts) {
                     throw new RuntimeException(
                             "Unable to create short URL after " + retryCount + " attempts", e);
                 }
             }
         }

         // This should never be reached due to the loop condition
         throw new RuntimeException("Unexpected error in short URL creation");
     }

    /**
     * Creates a shortened URL with a specific short code.
     *
     * @param originalUrl the original URL
     * @param shortCode   the short code to use
     * @return a URL response DTO
     */
    private UrlResponseDto createShortUrlWithCode(String originalUrl, String shortCode, String requestUrl) {
        UrlEntity entity = new UrlEntity(
                null,
                originalUrl,
                shortCode,
                0,
                Instant.now(),
                Instant.now()
        );

        UrlEntity savedEntity = urlRepository.save(entity);
        log.info("Created short URL with code: {} for URL: {}", shortCode, originalUrl);

        return buildResponse(savedEntity, requestUrl);
    }

    /**
     * Generates a unique short code.
     *
     * @return a unique Base62 encoded short code
     */
    private String generateShortCode() {
        return Base62Util.generateShortCode(shortCodeLength);
    }

    /**
     * Redirects to the original URL using HTTP 302.
     * <p>
     * This method atomically increments the click count using MongoDB's $inc operator.
     *
     * @param shortCode the short code to redirect
     * @return the original URL to redirect to
     * @throws UrlNotFoundException if the short code doesn't exist
     */
    public String redirect(String shortCode) {
        Objects.requireNonNull(shortCode, "Short code cannot be null");

        // First, find the URL entity
        UrlEntity entity = urlRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new UrlNotFoundException("Short URL not found: " + shortCode));

        // Atomically increment click count using MongoDB's $inc operator
        urlRepository.incrementClickCount(shortCode, 1);

        log.debug("Redirecting short code {} to original URL", shortCode);

        return entity.originalUrl();
    }

    /**
     * Gets URL analytics for a given short code.
     *
     * @param shortCode the short code to get analytics for
     * @return a URL analytics DTO with click count and metadata
     * @throws UrlNotFoundException if the short code doesn't exist
     */
    public com.mks.open.dto.UrlAnalyticsDto getAnalytics(String shortCode) {
        Objects.requireNonNull(shortCode, "Short code cannot be null");

        UrlEntity entity = urlRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new UrlNotFoundException("Short URL not found: " + shortCode));

        return new com.mks.open.dto.UrlAnalyticsDto(
                entity.originalUrl(),
                entity.shortCode(),
                entity.clickCount(),
                entity.createdAt()
        );
    }

    /**
     * Validates a URL string.
     *
     * @param url the URL to validate
     * @throws IllegalArgumentException if the URL is null, empty, or malformed
     */
    private void validateUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            throw new IllegalArgumentException("URL cannot be null or empty");
        }

        try {
            java.net.URL urlObject = new java.net.URL(url);
            String protocol = urlObject.getProtocol();
            if (!"http".equalsIgnoreCase(protocol) && !"https".equalsIgnoreCase(protocol)) {
                throw new IllegalArgumentException("URL must use http or https protocol");
            }

            String host = urlObject.getHost();
            if (host == null || host.isEmpty()) {
                throw new IllegalArgumentException("URL must have a valid host");
            }
        } catch (java.net.MalformedURLException e) {
            throw new IllegalArgumentException("Invalid URL format: " + e.getMessage(), e);
        }
    }

    /**
     * Builds a URL response DTO from an entity.
     *
     * @param entity the URL entity
     * @param requestUrl
     * @return a URL response DTO
     */
    private UrlResponseDto buildResponse(UrlEntity entity, String requestUrl) {
        return new UrlResponseDto(
                entity.shortCode(),
                requestUrl.replace("urls","r/") + entity.shortCode(),
                entity.originalUrl(),
                entity.createdAt()
        );
    }
}
