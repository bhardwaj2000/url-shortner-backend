package com.mks.open.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests for HealthController.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("HealthController Tests")
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Should return health status OK")
    void testHealth() throws Exception {
        mockMvc.perform(get("/api/v1/health/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"))
            .andExpect(jsonPath("$.database").exists())
            .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("Should return application info")
    void testInfo() throws Exception {
        mockMvc.perform(get("/api/v1/health/info"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.appName").value("url-shortner-backend"))
            .andExpect(jsonPath("$.version").value("1.0.0"))
            .andExpect(jsonPath("$.description").exists())
            .andExpect(jsonPath("$.javaVersion").exists());
    }
}





