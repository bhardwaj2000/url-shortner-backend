package com.mks.open.dto;

import java.time.Instant;

/**
 * Response DTO for URL analytics operations.
 * <p>
 * Immutable record class containing URL analytics information.
 *
 * @param originalUrl the original URL
 * @param shortCode   the short code
 * @param clickCount  the number of times the short URL has been clicked
 * @param createdAt   the timestamp when the URL was created
 */
public record UrlAnalyticsDto(
        String originalUrl,
        String shortCode,
        int clickCount,
        Instant createdAt
) {
}
