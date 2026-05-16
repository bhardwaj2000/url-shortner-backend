package com.mks.open.mapper;

import com.mks.open.dto.UrlRequestDto;
import com.mks.open.dto.UrlResponseDto;
import com.mks.open.entity.UrlEntity;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.function.Function;

/**
 * Mapper class for converting between DTOs and Entity objects.
 * <p>
 * This class follows the functional programming pattern using {@link Function}
 * for clean, immutable transformations.
 *
 * @author DevTeam
 */
@Component
public class UrlMapper {

    /**
     * Converts a URL request DTO to a URL entity.
     *
     * @param request the URL request DTO
     * @return a new URL entity
     */
    public UrlEntity toEntity(UrlRequestDto request) {
        return new UrlEntity(
                null,
                request.originalUrl(),
                null,
                0,
                Instant.now(),
                Instant.now()
        );
    }

    /**
     * Converts a URL entity to a URL response DTO.
     *
     * @param entity the URL entity
     * @return a URL response DTO
     */
    public UrlResponseDto toResponse(UrlEntity entity) {
        return new UrlResponseDto(
                entity.shortCode(),
                entity.shortCode() != null
                        ? "http://localhost:8080/" + entity.shortCode()
                        : null,
                entity.originalUrl(),
                entity.createdAt()
        );
    }

    /**
     * Converts a URL entity to an analytics response DTO.
     *
     * @param entity the URL entity
     * @return an analytics response DTO
     */
    public com.mks.open.dto.UrlAnalyticsDto toAnalyticsDto(UrlEntity entity) {
        return new com.mks.open.dto.UrlAnalyticsDto(
                entity.originalUrl(),
                entity.shortCode(),
                entity.clickCount(),
                entity.createdAt()
        );
    }

    /**
     * Updates the click count and updated at timestamp on an entity.
     *
     * @param entity     the entity to update
     * @param clickCount the new click count
     * @return the updated entity
     */
    public UrlEntity updateClickCount(UrlEntity entity, int clickCount) {
        return new UrlEntity(
                entity.id(),
                entity.originalUrl(),
                entity.shortCode(),
                clickCount,
                entity.createdAt(),
                Instant.now()
        );
    }

    /**
     * Creates a URL entity from a short code (for redirect operations).
     *
     * @param originalUrl the original URL
     * @param shortCode   the short code
     * @param clickCount  the current click count
     * @param createdAt   the creation timestamp
     * @return a new URL entity
     */
    public UrlEntity fromShortCode(String originalUrl, String shortCode, int clickCount, Instant createdAt) {
        return new UrlEntity(null, originalUrl, shortCode, clickCount, createdAt, Instant.now());
    }
}
