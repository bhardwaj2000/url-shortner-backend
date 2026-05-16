package com.mks.open.mapper;

import com.mks.open.dto.UrlAnalyticsDto;
import com.mks.open.dto.UrlRequestDto;
import com.mks.open.dto.UrlResponseDto;
import com.mks.open.entity.UrlEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for UrlMapper.
 */
@DisplayName("UrlMapper Tests")
class UrlMapperTest {

    private UrlMapper mapper;
    private Instant now;

    @BeforeEach
    void setUp() {
        mapper = new UrlMapper();
        now = Instant.now();
    }

    @Test
    @DisplayName("Should convert UrlRequestDto to UrlEntity")
    void testToEntity() {
        UrlRequestDto request = new UrlRequestDto("https://example.com/long/url");

        UrlEntity entity = mapper.toEntity(request);

        assertNull(entity.id());
        assertEquals("https://example.com/long/url", entity.originalUrl());
        assertNull(entity.shortCode());
        assertEquals(0, entity.clickCount());
        assertNotNull(entity.createdAt());
        assertNotNull(entity.updatedAt());
    }

    @Test
    @DisplayName("Should convert UrlEntity to UrlResponseDto")
    void testToResponse() {
        UrlEntity entity = new UrlEntity(
            "id123",
            "https://example.com",
            "abc123",
            0,
            now,
            now
        );

        UrlResponseDto response = mapper.toResponse(entity);

        assertEquals("abc123", response.shortCode());
        assertEquals("http://localhost:8080/abc123", response.shortUrl());
        assertEquals("https://example.com", response.originalUrl());
        assertEquals(now, response.createdAt());
    }

    @Test
    @DisplayName("Should handle null shortCode in toResponse")
    void testToResponseNullShortCode() {
        UrlEntity entity = new UrlEntity(
            "id123",
            "https://example.com",
            null,
            0,
            now,
            now
        );

        UrlResponseDto response = mapper.toResponse(entity);

        assertNull(response.shortCode());
        assertNull(response.shortUrl());
        assertEquals("https://example.com", response.originalUrl());
    }

    @Test
    @DisplayName("Should convert UrlEntity to UrlAnalyticsDto")
    void testToAnalyticsDto() {
        UrlEntity entity = new UrlEntity(
            "id123",
            "https://example.com",
            "abc123",
            15,
            now,
            now
        );

        UrlAnalyticsDto analytics = mapper.toAnalyticsDto(entity);

        assertEquals("https://example.com", analytics.originalUrl());
        assertEquals("abc123", analytics.shortCode());
        assertEquals(15, analytics.clickCount());
        assertEquals(now, analytics.createdAt());
    }

    @Test
    @DisplayName("Should update click count")
    void testUpdateClickCount() {
        UrlEntity entity = new UrlEntity(
            "id123",
            "https://example.com",
            "abc123",
            5,
            now,
            now
        );

        UrlEntity updated = mapper.updateClickCount(entity, 10);

        assertEquals(10, updated.clickCount());
        assertEquals("id123", updated.id());
        assertEquals("abc123", updated.shortCode());
        assertEquals(now, updated.createdAt());
    }

    @Test
    @DisplayName("Should create entity from short code")
    void testFromShortCode() {
        Instant createdAt = Instant.now();

        UrlEntity entity = mapper.fromShortCode("https://example.com", "abc123", 5, createdAt);

        assertNull(entity.id());
        assertEquals("https://example.com", entity.originalUrl());
        assertEquals("abc123", entity.shortCode());
        assertEquals(5, entity.clickCount());
        assertEquals(createdAt, entity.createdAt());
    }

    @Test
    @DisplayName("Should map DTO to Entity to Response correctly")
    void testEndToEndMapping() {
        UrlRequestDto request = new UrlRequestDto("https://github.com/example");
        UrlEntity entity = mapper.toEntity(request);
        entity = new UrlEntity(entity.id(), entity.originalUrl(), "xyz789", 0, entity.createdAt(), entity.updatedAt());

        UrlResponseDto response = mapper.toResponse(entity);

        assertEquals("https://github.com/example", response.originalUrl());
        assertEquals("xyz789", response.shortCode());
        assertEquals("http://localhost:8080/xyz789", response.shortUrl());
    }
}

