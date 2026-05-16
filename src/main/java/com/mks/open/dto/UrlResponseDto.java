package com.mks.open.dto;

import java.time.Instant;

/**
 * Response DTO for URL shortening operations.
 * <p>
 * Immutable record class containing the short URL information.
 *
 * @param shortCode   the generated short code (6-8 characters, Base62 encoded)
 * @param shortUrl    the full short URL that redirects to original URL
 * @param originalUrl the original URL that was shortened
 * @param createdAt   the timestamp when the URL was created
 */
public record UrlResponseDto(
        String shortCode,
        String shortUrl,
        String originalUrl,
        Instant createdAt
) {
}
