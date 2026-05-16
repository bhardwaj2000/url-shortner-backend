package com.mks.open.service;

import com.mks.open.dto.UrlRequestDto;
import com.mks.open.dto.UrlResponseDto;
import com.mks.open.entity.UrlEntity;
import com.mks.open.exception.UrlNotFoundException;
import com.mks.open.repository.UrlRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link UrlService}.
 * <p>
 * Tests cover:
 * <ul>
 *   <li>URL shortening creation</li>
 *   <li>Redirect functionality</li>
 *   <li>Analytics retrieval</li>
 *   <li>Validation and error handling</li>
 * </ul>
 *
 * @author DevTeam
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UrlService Unit Tests")
class UrlServiceTest {

    @Mock
    private UrlRepository urlRepository;

    @InjectMocks
    private UrlService urlService;

    private UrlRequestDto validRequest;
    private UrlEntity testEntity;

    @BeforeEach
    void setUp() {
        validRequest = new UrlRequestDto("https://example.com/very/long/url/path");

        testEntity = new UrlEntity(
                "1234567890",
                "https://example.com/very/long/url/path",
                "aB12Cd",
                0,
                Instant.now(),
                Instant.now()
        );
    }

    @Test
    @DisplayName("Should successfully create a short URL")
    void createShortUrl_Success() {
        // Arrange - Return the entity with the short code that was passed in
        doAnswer(invocation -> {
            UrlEntity entity = invocation.getArgument(0);
            return new UrlEntity(
                    "1234567890",
                    entity.originalUrl(),
                    entity.shortCode(),  // Use the same short code from the passed entity
                    0,
                    entity.createdAt(),
                    entity.updatedAt()
            );
        }).when(urlRepository).save(any(UrlEntity.class));
        // Mock the idempotency check to return empty (no existing URL found)
        when(urlRepository.findByOriginalUrl(validRequest.originalUrl()))
                .thenReturn(Optional.empty());

        // Act
        UrlResponseDto response = urlService.createShortUrl(validRequest);

        // Assert
        assertNotNull(response);
        // The short code is randomly generated, but it should be valid
        String shortCode = response.shortCode();
        assertNotNull(shortCode);
        assertTrue(shortCode.matches("^[a-zA-Z0-9]{6}$"));
        assertNotNull(response.shortUrl());
        assertEquals(validRequest.originalUrl(), response.originalUrl());
        assertNotNull(response.createdAt());
    }

    @Test
    @DisplayName("Should throw exception for invalid URL")
    void createShortUrl_InvalidUrl_ThrowsException() {
        // Arrange
        UrlRequestDto invalidRequest = new UrlRequestDto("not-a-valid-url");

        // Act & Assert
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> urlService.createShortUrl(invalidRequest)
        );

        assertTrue(exception.getMessage().contains("Invalid URL"));
    }

    @Test
    @DisplayName("Should throw exception for null URL")
    void createShortUrl_NullUrl_ThrowsException() {
        // Arrange
        UrlRequestDto nullRequest = new UrlRequestDto(null);

        // Act & Assert
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> urlService.createShortUrl(nullRequest)
        );

        assertTrue(exception.getMessage().contains("cannot be null or empty"));
    }

    @Test
    @DisplayName("Should successfully redirect to original URL")
    void redirect_Success() {
        // Arrange
        String shortCode = "aB12Cd";
        when(urlRepository.findByShortCode(shortCode))
                .thenReturn(Optional.of(testEntity));
        when(urlRepository.incrementClickCount(shortCode, 1))
                .thenReturn(1L);

        // Act
        String originalUrl = urlService.redirect(shortCode);

        // Assert
        assertEquals(testEntity.originalUrl(), originalUrl);
        verify(urlRepository, times(1)).incrementClickCount(shortCode, 1);
    }

    @Test
    @DisplayName("Should throw exception when redirecting with invalid short code")
    void redirect_InvalidCode_ThrowsException() {
        // Arrange
        String invalidCode = "invalid";
        when(urlRepository.findByShortCode(invalidCode))
                .thenReturn(Optional.empty());

        // Act & Assert
        UrlNotFoundException exception = assertThrows(
                UrlNotFoundException.class,
                () -> urlService.redirect(invalidCode)
        );

        assertTrue(exception.getMessage().contains("not found"));
    }

    @Test
    @DisplayName("Should throw exception for null short code in redirect")
    void redirect_NullCode_ThrowsException() {
        // Act & Assert
        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> urlService.redirect(null)
        );

        assertTrue(exception.getMessage().contains("cannot be null"));
    }

    @Test
    @DisplayName("Should successfully get analytics")
    void getAnalytics_Success() {
        // Arrange
        String shortCode = "aB12Cd";
        when(urlRepository.findByShortCode(shortCode))
                .thenReturn(Optional.of(testEntity));

        // Act
        com.mks.open.dto.UrlAnalyticsDto analytics = urlService.getAnalytics(shortCode);

        // Assert
        assertNotNull(analytics);
        assertEquals(testEntity.originalUrl(), analytics.originalUrl());
        assertEquals(testEntity.shortCode(), analytics.shortCode());
        assertEquals(testEntity.clickCount(), analytics.clickCount());
    }

    @Test
    @DisplayName("Should throw exception when getting analytics for non-existent code")
    void getAnalytics_InvalidCode_ThrowsException() {
        // Arrange
        String invalidCode = "invalid";
        when(urlRepository.findByShortCode(invalidCode))
                .thenReturn(Optional.empty());

        // Act & Assert
        UrlNotFoundException exception = assertThrows(
                UrlNotFoundException.class,
                () -> urlService.getAnalytics(invalidCode)
        );

        assertTrue(exception.getMessage().contains("not found"));
    }

    @Test
    @DisplayName("Should return existing short code for duplicate URL")
    void createShortUrl_DuplicateUrl_ReturnsSameCode() {
        // Arrange
        UrlRequestDto request = new UrlRequestDto("https://example.com/duplicate");
        String existingCode = "existing123";

        UrlEntity existingEntity = new UrlEntity(
                "id123",
                "https://example.com/duplicate",
                existingCode,
                5,
                Instant.now(),
                Instant.now()
        );

        when(urlRepository.findByOriginalUrl("https://example.com/duplicate"))
                .thenReturn(java.util.Optional.of(existingEntity));

        // Act
        UrlResponseDto response = urlService.createShortUrl(request);

        // Assert
        assertEquals(existingCode, response.shortCode());
        assertEquals(request.originalUrl(), response.originalUrl());
    }

    @Test
    @DisplayName("Should handle empty string URL")
    void createShortUrl_EmptyUrl_ThrowsException() {
        // Arrange
        UrlRequestDto emptyRequest = new UrlRequestDto("");

        // Act & Assert
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> urlService.createShortUrl(emptyRequest)
        );

        assertTrue(exception.getMessage().contains("cannot be null or empty"));
    }

    @Test
    @DisplayName("Should validate URL has valid host")
    void createShortUrl_NoHost_ThrowsException() {
        // Arrange
        UrlRequestDto invalidRequest = new UrlRequestDto("https://");

        // Act & Assert
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> urlService.createShortUrl(invalidRequest)
        );

        assertTrue(exception.getMessage().contains("host"));
    }
}
