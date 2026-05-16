package com.mks.open.repository;

import com.mks.open.entity.UrlEntity;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for {@link UrlRepository}.
 * <p>
 * Requires MongoDB to be running. Uses embedded MongoDB for CI/CD.
 *
 * @author DevTeam
 */
@ActiveProfiles("test")
@SpringBootTest
@DisplayName("UrlRepository Integration Tests")
class UrlRepositoryTest {

    @Autowired
    private UrlRepository urlRepository;

    @Autowired
    private MongoTemplate mongoTemplate;

    @BeforeEach
    void setUp() {
        // Clear the collection before each test
        mongoTemplate.getCollectionNames().stream()
            .filter(name -> name.startsWith("shortner"))
            .forEach(name -> mongoTemplate.getCollection(name).deleteMany(new org.bson.Document()));
    }

    @AfterEach
    void tearDown() {
        // Clear the collection after each test
        mongoTemplate.getCollectionNames().stream()
            .filter(name -> name.startsWith("shortner"))
            .forEach(name -> mongoTemplate.getCollection(name).deleteMany(new org.bson.Document()));
    }

    @Test
    @DisplayName("Should save and retrieve URL entity")
    void saveAndFindByShortCode_Success() {
        // Arrange
        UrlEntity entity = new UrlEntity(
            null,
            "https://example.com/test",
            "aB12Cd",
            0,
            Instant.now(),
            Instant.now()
        );

        // Act
        UrlEntity saved = urlRepository.save(entity);
        UrlEntity found = urlRepository.findByShortCode("aB12Cd").orElse(null);

        // Assert
        assertNotNull(saved);
        assertNotNull(saved.id());
        assertEquals(entity.shortCode(), saved.shortCode());

        assertNotNull(found);
        assertEquals(saved.shortCode(), found.shortCode());
        assertEquals(saved.originalUrl(), found.originalUrl());
    }

    @Test
    @DisplayName("Should update click count atomically")
    void incrementClickCount_Success() {
        // Arrange
        UrlEntity entity = new UrlEntity(
            null,
            "https://example.com/test",
            "testCode123",
            0,
            Instant.now(),
            Instant.now()
        );
        urlRepository.save(entity);

        // Act
        int modifiedCount = Math.toIntExact(urlRepository.incrementClickCount("testCode123", 1));

        // Assert
        assertEquals(1, modifiedCount);

        UrlEntity updated = urlRepository.findByShortCode("testCode123").orElseThrow();
        assertEquals(1, updated.clickCount());
    }

    @Test
    @DisplayName("Should handle multiple concurrent increments")
    void concurrentIncrements_Success() {
        // Arrange
        String shortCode = "concurrentTest123"+ UUID.randomUUID();
        UrlEntity entity = new UrlEntity(
            null,
            "https://example.com/test"+ UUID.randomUUID(),
            shortCode,
            0,
            Instant.now(),
            Instant.now()
        );
        urlRepository.save(entity);

        // Act - Simulate concurrent clicks
        Runnable incrementTask = () -> {
            for (int i = 0; i < 20; i++) {
                urlRepository.incrementClickCount(shortCode, 1);
            }
        };

        Thread t1 = new Thread(incrementTask);
        Thread t2 = new Thread(incrementTask);
        Thread t3 = new Thread(incrementTask);

        t1.start();
        t2.start();
        t3.start();

        try {
            t1.join();
            t2.join();
            t3.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Assert
        UrlEntity updated = urlRepository.findByShortCode(shortCode).orElseThrow();
        assertEquals(60, updated.clickCount());
    }

    @Test
    @DisplayName("Should throw exception when saving duplicate short code")
    void save_DuplicateCode_ThrowsException() {
        // Arrange
        UrlEntity entity1 = new UrlEntity(
            null,
            "https://example.com/test1",
            "duplicate123",
            0,
            Instant.now(),
            Instant.now()
        );
        urlRepository.save(entity1);

        UrlEntity entity2 = new UrlEntity(
            null,
            "https://example.com/test2",
            "duplicate123",
            0,
            Instant.now(),
            Instant.now()
        );

        // Act & Assert
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> {
            urlRepository.save(entity2);
        });
    }

    @Test
    @DisplayName("Should check if short code exists")
    void existsByShortCode_Success() {
        // Arrange
        UrlEntity entity = new UrlEntity(
            null,
            "https://example.com/test",
            "existsCheck123",
            0,
            Instant.now(),
            Instant.now()
        );
        urlRepository.save(entity);

        // Act & Assert
        assertTrue(urlRepository.existsByShortCode("existsCheck123"));
        assertFalse(urlRepository.existsByShortCode("nonexistent"));
    }

    @Test
    @DisplayName("Should return empty Optional when code not found")
    void findByShortCode_NotFound() {
        // Act
        Optional<UrlEntity> result = urlRepository.findByShortCode("nonexistent");

        // Assert
        assertFalse(result.isPresent());
    }
}
