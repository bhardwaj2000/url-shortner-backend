package com.mks.open.entity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for UrlEntity record methods.
 */
@DisplayName("UrlEntity Tests")
class UrlEntityTest {

    private UrlEntity entity;
    private Instant now;

    @BeforeEach
    void setUp() {
        now = Instant.now();
        entity = new UrlEntity(
            "id123",
            "https://example.com",
            "abc123",
            5,
            now,
            now
        );
    }

    @Test
    @DisplayName("Should create entity with all fields")
    void testEntityCreation() {
        assertNotNull(entity.id());
        assertEquals("id123", entity.id());
        assertEquals("https://example.com", entity.originalUrl());
        assertEquals("abc123", entity.shortCode());
        assertEquals(5, entity.clickCount());
        assertNotNull(entity.createdAt());
        assertNotNull(entity.updatedAt());
    }

    @Test
    @DisplayName("Should update click count and generate new updatedAt")
    void testWithClickCount() {
        UrlEntity updated = entity.withClickCount(10);

        assertEquals(10, updated.clickCount());
        assertEquals(entity.id(), updated.id());
        assertEquals(entity.originalUrl(), updated.originalUrl());
        assertEquals(entity.shortCode(), updated.shortCode());
    }

    @Test
    @DisplayName("Should increment click count")
    void testIncrementClickCount() throws InterruptedException {
        UrlEntity incremented = entity.incrementClickCount();

        assertEquals(6, incremented.clickCount());
        assertEquals(entity.id(), incremented.id());
    }

    @Test
    @DisplayName("Should update original URL and generate new updatedAt")
    void testWithOriginalUrl() throws InterruptedException {
        UrlEntity updated = entity.withOriginalUrl("https://newurl.com");

        assertEquals("https://newurl.com", updated.originalUrl());
        assertEquals(entity.id(), updated.id());
        assertEquals(entity.shortCode(), updated.shortCode());
        assertEquals(entity.clickCount(), updated.clickCount());
    }

    @Test
    @DisplayName("Should chain multiple updates")
    void testChainedUpdates() {
        UrlEntity updated = entity
            .withClickCount(100)
            .incrementClickCount()
            .withOriginalUrl("https://chained.com");

        assertEquals(101, updated.clickCount());
        assertEquals("https://chained.com", updated.originalUrl());
    }

    @Test
    @DisplayName("Should preserve immutability")
    void testImmutability() {
        UrlEntity updated = entity.withClickCount(50);

        assertEquals(5, entity.clickCount());
        assertEquals(50, updated.clickCount());
    }
}

