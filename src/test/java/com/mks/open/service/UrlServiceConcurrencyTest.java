package com.mks.open.service;

import com.mks.open.dto.UrlRequestDto;
import com.mks.open.dto.UrlResponseDto;
import com.mks.open.entity.UrlEntity;
import com.mks.open.repository.UrlRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for URL Service concurrency handling.
 * <p>
 * Tests verify that:
 * 1. Concurrent requests for the same URL return the same short code (idempotency)
 * 2. Different URLs get different short codes
 * 3. Click counts are updated atomically
 *
 * @author DevTeam
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("URL Service Concurrency Tests")
class UrlServiceConcurrencyTest {

    @Autowired
    private UrlService urlService;

    @Autowired
    private UrlRepository urlRepository;

    private static final String TEST_URL = "https://github.com/example/very/long/url/path";

    @BeforeEach
    void setUp() {
        urlRepository.deleteAll();
    }

    @Test
    @DisplayName("Concurrent requests for same URL should return same short code (Idempotency)")
    void testConcurrentDuplicateUrlRequests() throws InterruptedException {
        int threadCount = 5;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);

        AtomicReference<String> firstShortCode = new AtomicReference<>();
        UrlRequestDto request = new UrlRequestDto(TEST_URL);

        // Launch multiple threads requesting the same URL simultaneously
        for (int i = 0; i < threadCount; i++) {
            new Thread(() -> {
                try {
                    startLatch.await(); // Wait for all threads to be ready
                    UrlResponseDto response = urlService.createShortUrl(request);

                    if (firstShortCode.get() == null) {
                        firstShortCode.set(response.shortCode());
                    }

                    // All responses should have the same short code
                    assertEquals(firstShortCode.get(), response.shortCode(),
                            "All concurrent requests should return the same short code");

                } catch (Exception e) {
                    fail("Exception during concurrent URL shortening: " + e.getMessage());
                } finally {
                    endLatch.countDown();
                }
            }).start();
        }

        // Release all threads simultaneously
        startLatch.countDown();
        endLatch.await();

        // Verify database has only one entry for this URL
        Optional<UrlEntity> savedEntity = urlRepository.findByOriginalUrl(TEST_URL);
        assertTrue(savedEntity.isPresent(), "URL should be saved in database");
        assertEquals(firstShortCode.get(), savedEntity.get().shortCode());

        // Verify count of entries for this URL
        long countForUrl = urlRepository.count();
        assertEquals(1, countForUrl, "Database should have exactly one entry for this URL");
    }

    @Test
    @DisplayName("Concurrent requests for different URLs should return different short codes")
    void testConcurrentDifferentUrlRequests() throws InterruptedException {
        int threadCount = 3;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);

        String[] shortCodes = new String[threadCount];
        String[] testUrls = {
                "https://example.com/url1",
                "https://example.com/url2",
                "https://example.com/url3"
        };

        // Launch threads with different URLs
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            new Thread(() -> {
                try {
                    startLatch.await();
                    UrlRequestDto request = new UrlRequestDto(testUrls[index]);
                    UrlResponseDto response = urlService.createShortUrl(request);
                    shortCodes[index] = response.shortCode();
                } catch (Exception e) {
                    fail("Exception during concurrent URL shortening: " + e.getMessage());
                } finally {
                    endLatch.countDown();
                }
            }).start();
        }

        startLatch.countDown();
        endLatch.await();

        // Verify all short codes are different
        assertNotEquals(shortCodes[0], shortCodes[1], "Different URLs should have different short codes");
        assertNotEquals(shortCodes[1], shortCodes[2], "Different URLs should have different short codes");
        assertNotEquals(shortCodes[0], shortCodes[2], "Different URLs should have different short codes");

        // Verify database has three entries
        assertEquals(3, urlRepository.count(), "Database should have three entries");
    }

    @Test
    @DisplayName("Sequential requests for same URL should return same short code")
    void testSequentialDuplicateUrlRequests() {
        UrlRequestDto request = new UrlRequestDto(TEST_URL);

        UrlResponseDto response1 = urlService.createShortUrl(request);
        UrlResponseDto response2 = urlService.createShortUrl(request);
        UrlResponseDto response3 = urlService.createShortUrl(request);

        // All responses should have the same short code
        assertEquals(response1.shortCode(), response2.shortCode(),
                "Sequential requests for same URL should return same short code");
        assertEquals(response2.shortCode(), response3.shortCode(),
                "Sequential requests for same URL should return same short code");

        // Database should have exactly one entry
        assertEquals(1, urlRepository.count(), "Database should have exactly one entry");
        assertEquals(response1.shortCode(), response3.shortCode());
    }

    @Test
    @DisplayName("Concurrent redirects should atomically increment click count")
    void testConcurrentClickCountIncrement() throws InterruptedException {
        // First, create a shortened URL
        UrlRequestDto request = new UrlRequestDto(TEST_URL);
        UrlResponseDto response = urlService.createShortUrl(request);
        String shortCode = response.shortCode();

        // Verify initial click count is 0
        UrlEntity entity = urlRepository.findByShortCode(shortCode).orElseThrow();
        assertEquals(0, entity.clickCount(), "Initial click count should be 0");

        // Now simulate concurrent redirects
        int threadCount = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            new Thread(() -> {
                try {
                    startLatch.await();
                    urlService.redirect(shortCode);
                } catch (Exception e) {
                    fail("Exception during redirect: " + e.getMessage());
                } finally {
                    endLatch.countDown();
                }
            }).start();
        }

        startLatch.countDown();
        endLatch.await();

        // Verify click count was atomically incremented
        UrlEntity finalEntity = urlRepository.findByShortCode(shortCode).orElseThrow();
        assertEquals(threadCount, finalEntity.clickCount(),
                "Click count should be atomically incremented to " + threadCount);
    }

    @Test
    @DisplayName("Mixed concurrent operations (create and redirect)")
    void testMixedConcurrentOperations() throws InterruptedException {
        // First create a URL
        UrlRequestDto request = new UrlRequestDto(TEST_URL);
        UrlResponseDto response = urlService.createShortUrl(request);
        String shortCode = response.shortCode();

        int createThreads = 3;
        int redirectThreads = 7;
        int totalThreads = createThreads + redirectThreads;

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(totalThreads);

        // Create threads that try to shorten the same URL
        for (int i = 0; i < createThreads; i++) {
            new Thread(() -> {
                try {
                    startLatch.await();
                    urlService.createShortUrl(request);
                } catch (Exception e) {
                    fail("Exception during concurrent create: " + e.getMessage());
                } finally {
                    endLatch.countDown();
                }
            }).start();
        }

        // Create threads that redirect
        for (int i = 0; i < redirectThreads; i++) {
            new Thread(() -> {
                try {
                    startLatch.await();
                    urlService.redirect(shortCode);
                } catch (Exception e) {
                    fail("Exception during concurrent redirect: " + e.getMessage());
                } finally {
                    endLatch.countDown();
                }
            }).start();
        }

        startLatch.countDown();
        endLatch.await();

        // Verify results
        UrlEntity finalEntity = urlRepository.findByShortCode(shortCode).orElseThrow();

        // Should have exactly redirectThreads clicks (7) because creates are idempotent
        assertEquals(redirectThreads, finalEntity.clickCount(),
                "Click count should match redirect thread count");

        // Should still have only one entry for the URL
        assertEquals(1, urlRepository.count(), "Should have exactly one URL entry");
    }
}

