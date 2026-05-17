package com.mks.open.exception;

import com.mks.open.dto.ErrorResponse;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests for GlobalExceptionHandler.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("GlobalExceptionHandler Tests")
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Should handle UrlNotFoundException with 404")
    void testUrlNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/urls/nonexistent"))
            .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("Should handle invalid short code format with 400")
    void testInvalidShortCodeFormat() throws Exception {
        mockMvc.perform(get("/api/v1/r/abc"))
            .andExpect(status().is4xxClientError());
    }
}

