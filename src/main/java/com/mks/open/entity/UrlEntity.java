package com.mks.open.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * MongoDB document entity for URL mappings.
 * <p>
 * Represents a shortened URL mapping with click tracking.
 * <p>
 * Indexing Strategy:
 * <ul>
 *   <li>shortCode: Unique index for O(1) lookup and collision prevention</li>
 *   <li>createdAt: Non-unique index for sorting by creation time</li>
 * </ul>
 * <p>
 * Thread-Safety: This entity is used with MongoDB atomic operations
 * to ensure thread-safe concurrent updates.
 *
 * @param id          the unique MongoDB document ID
 * @param originalUrl the original URL to be shortened
 * @param shortCode   the unique short code (Base62 encoded)
 * @param clickCount  the number of times this URL has been accessed
 * @param createdAt   the timestamp when this URL was created
 * @param updatedAt   the timestamp when this URL was last updated
 */
@Document(collection = "shortner")
public record UrlEntity(
        @Id String id,
        String originalUrl,
        String shortCode,
        int clickCount,
        Instant createdAt,
        Instant updatedAt
) {
    /**
     * Creates a new UrlEntity with an updated click count.
     *
     * @param newClickCount the new click count value
     * @return a new UrlEntity with the updated click count
     */
    public UrlEntity withClickCount(int newClickCount) {
        return new UrlEntity(
                this.id,
                this.originalUrl,
                this.shortCode,
                newClickCount,
                this.createdAt,
                Instant.now()
        );
    }

    /**
     * Increments the click count atomically (for informational purposes).
     *
     * @return a new UrlEntity with incremented click count
     */
    public UrlEntity incrementClickCount() {
        return withClickCount(this.clickCount + 1);
    }

    /**
     * Creates a new UrlEntity with an updated original URL.
     *
     * @param newOriginalUrl the new original URL
     * @return a new UrlEntity with the updated URL
     */
    public UrlEntity withOriginalUrl(String newOriginalUrl) {
        return new UrlEntity(
                this.id,
                newOriginalUrl,
                this.shortCode,
                this.clickCount,
                this.createdAt,
                Instant.now()
        );
    }
}
