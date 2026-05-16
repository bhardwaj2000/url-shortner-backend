package com.mks.open;


import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@SpringBootTest
class UrlShortnerBackendApplicationTests {

	@Test
	void contextLoads() {
	}

    // Additional tests can be added here to verify application context and basic bean loading.
    @Test
    void testApplicationContextLoads() {
        // This test will pass if the application context loads successfully.
    assertDoesNotThrow(()-> UrlShortnerBackendApplication.main(new String[]{}));
    }

}
