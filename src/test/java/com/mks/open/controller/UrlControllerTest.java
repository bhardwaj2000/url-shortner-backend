package com.mks.open.controller;

import com.mks.open.dto.UrlRequestDto;
import com.mks.open.dto.UrlResponseDto;
import com.mks.open.exception.GlobalExceptionHandler;
import com.mks.open.service.UrlService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for {@link UrlController}.
 * <p>
 * Tests HTTP endpoints using Spring MVC Test framework.
 *
 * @author DevTeam
 */
@WebMvcTest(
    controllers = {UrlController.class, GlobalExceptionHandler.class}
)
@DisplayName("UrlController Integration Tests")
class UrlControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UrlService urlService;

    private UrlResponseDto testResponse;

    @BeforeEach
    void setUp() {
        testResponse = new UrlResponseDto(
            "aB12Cd",
            "http://localhost:8080/aB12Cd",
            "https://example.com/very/long/url",
            Instant.now()
        );
    }

    @Test
    @DisplayName("Should create short URL successfully")
    void createShortUrl_Success() throws Exception {
        // Arrange
        UrlRequestDto request = new UrlRequestDto("https://example.com/very/long/url");

        // Act & Assert
        when(urlService.createShortUrl(any(UrlRequestDto.class), any(HttpServletRequest.class)))
            .thenReturn(testResponse);

        mockMvc.perform(post("/api/v1/urls")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.shortCode").value("aB12Cd"))
            .andExpect(jsonPath("$.originalUrl").value(request.originalUrl()))
            .andExpect(jsonPath("$.shortUrl").value(testResponse.shortUrl()))
            .andExpect(jsonPath("$.createdAt").isNotEmpty());

        verify(urlService, times(1)).createShortUrl(any(UrlRequestDto.class), any(HttpServletRequest.class));
    }

    @Test
    @DisplayName("Should return 400 for invalid URL")
    void createShortUrl_InvalidUrl_ReturnsBadRequest() throws Exception {
        // Act & Assert
        // @NotBlank validates, but URL format validation happens in service
        // The controller uses @Valid on @RequestBody which triggers validation
        // "not-a-valid-url" is not blank but is not a valid URL format
        // Since it passes @NotBlank, it reaches the service which throws exception
        when(urlService.createShortUrl(any(UrlRequestDto.class), any(HttpServletRequest.class)))
            .thenThrow(new IllegalArgumentException("Invalid URL format"));

        mockMvc.perform(post("/api/v1/urls")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new UrlRequestDto("not-a-valid-url"))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.error").value("Bad Request"));
    }

    @Test
    @DisplayName("Should return 400 for empty URL")
    void createShortUrl_EmptyUrl_ReturnsBadRequest() throws Exception {
        // Act & Assert
        // @NotBlank validation fails on empty originalUrl, service is never called
        UrlRequestDto emptyRequest = new UrlRequestDto("");
        mockMvc.perform(post("/api/v1/urls")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(emptyRequest)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("Should return 400 for missing request body")
    void createShortUrl_MissingBody_ReturnsBadRequest() throws Exception {
        // Act & Assert
        mockMvc.perform(post("/api/v1/urls")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should redirect to original URL")
    void redirect_Success() throws Exception {
        // Arrange
        when(urlService.redirect("aB12Cd"))
            .thenReturn("https://example.com/redirect-target");

        // Act & Assert
        mockMvc.perform(get("/api/v1/r/aB12Cd"))
            .andExpect(status().isFound())
            .andExpect(header().string("Location", "https://example.com/redirect-target"));

        verify(urlService, times(1)).redirect("aB12Cd");
    }

    @Test
    @DisplayName("Should return 404 for invalid redirect code")
    void redirect_InvalidCode_ReturnsNotFound() throws Exception {
        // Arrange
        when(urlService.redirect("invalid"))
            .thenThrow(new com.mks.open.exception.UrlNotFoundException("Short URL not found: invalid"));

        // Act & Assert
        mockMvc.perform(get("/api/v1/r/invalid"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.error").value("Not Found"));

        verify(urlService, times(1)).redirect("invalid");
    }

    @Test
    @DisplayName("Should get URL analytics")
    void getAnalytics_Success() throws Exception {
        // Arrange
        com.mks.open.dto.UrlAnalyticsDto analytics = new com.mks.open.dto.UrlAnalyticsDto(
            "https://example.com/original",
            "aB12Cd",
            15,
            Instant.now()
        );

        when(urlService.getAnalytics("aB12Cd"))
            .thenReturn(analytics);

        // Act & Assert
        mockMvc.perform(get("/api/v1/urls/aB12Cd"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.originalUrl").value(analytics.originalUrl()))
            .andExpect(jsonPath("$.shortCode").value(analytics.shortCode()))
            .andExpect(jsonPath("$.clickCount").value(analytics.clickCount()));

        verify(urlService, times(1)).getAnalytics("aB12Cd");
    }

    @Test
    @DisplayName("Should return 404 for invalid analytics code")
    void getAnalytics_InvalidCode_ReturnsNotFound() throws Exception {
        // Arrange
        when(urlService.getAnalytics("invalid"))
            .thenThrow(new com.mks.open.exception.UrlNotFoundException("Short URL not found: invalid"));

        // Act & Assert
        mockMvc.perform(get("/api/v1/urls/invalid"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404));

        verify(urlService, times(1)).getAnalytics("invalid");
    }

    @Test
    @DisplayName("Should validate short code format")
    void redirect_InvalidFormat_ReturnsBadRequest() throws Exception {
        // Act & Assert - code too short
        mockMvc.perform(get("/api/v1/r/abc"))
            .andExpect(status().is4xxClientError());

        // Act & Assert - code too long
        mockMvc.perform(get("/api/v1/r/abcde1234"))
            .andExpect(status().is4xxClientError());

        // Act & Assert - invalid characters
        mockMvc.perform(get("/api/v1/r/abc!@#"))
            .andExpect(status().is4xxClientError());
    }
}
