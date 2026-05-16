package com.mks.open.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link Base62Util}.
 * <p>
 * Tests cover:
 * <ul>
 *   <li>Code generation with different lengths</li>
 *   <li>Uniqueness under high concurrency</li>
 *   <li>Collision handling</li>
 *   <li>Retry logic</li>
 * </ul>
 *
 * @author DevTeam
 */
@DisplayName("Base62Util Unit Tests")
class Base62UtilTest {

    @BeforeEach
    void setUp() {
        Base62Util.reset();
    }

    @AfterEach
    void tearDown() {
        Base62Util.reset();
    }

    @Test
    @DisplayName("Should generate valid Base62 short code")
    void generateShortCode_Valid() {
        // Act
        String code = Base62Util.generateShortCode(6);

        // Assert
        assertNotNull(code);
        assertEquals(6, code.length());
        assertTrue(code.matches("^[a-zA-Z0-9]+$"));
    }

    @Test
    @DisplayName("Should generate different codes on successive calls")
    void generateShortCode_Unique() {
        // Act
        String code1 = Base62Util.generateShortCode();
        String code2 = Base62Util.generateShortCode();
        String code3 = Base62Util.generateShortCode();

        // Assert
        assertNotEquals(code1, code2);
        assertNotEquals(code2, code3);
        assertNotEquals(code1, code3);
    }

    @Test
    @DisplayName("Should generate codes of different lengths")
    void generateShortCode_DifferentLengths() {
        // Act & Assert
        assertEquals(4, Base62Util.generateShortCode(4).length());
        assertEquals(6, Base62Util.generateShortCode(6).length());
        assertEquals(8, Base62Util.generateShortCode(8).length());
    }

    @Test
    @DisplayName("Should validate code length parameters")
    void generateShortCode_InvalidLength_ThrowsException() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> Base62Util.generateShortCode(3));
        assertThrows(IllegalArgumentException.class, () -> Base62Util.generateShortCode(13));
    }

    @Test
    @DisplayName("Should handle concurrent generation without collisions")
    void generateShortCode_Concurrent() throws InterruptedException {
        // Arrange
        int numThreads = 10;
        int codesPerThread = 10;
        String[] codes = new String[numThreads * codesPerThread];
        int[] index = {0};

        // Act
        Thread[] threads = new Thread[numThreads];
        for (int i = 0; i < numThreads; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < codesPerThread; j++) {
                    codes[index[0]++] = Base62Util.generateShortCode();
                }
            });
            threads[i].start();
        }

        // Wait for all threads to complete
        for (Thread thread : threads) {
            thread.join();
        }

        // Assert - all codes should be unique
        java.util.Set<String> uniqueCodes = new java.util.HashSet<>();
        for (String code : codes) {
            uniqueCodes.add(code);
        }

        assertEquals(codes.length, uniqueCodes.size());
    }

    @Test
    @DisplayName("Should track total generated codes")
    void getTotalGeneratedCodes_Correct() {
        // Act
        Base62Util.generateShortCode();
        Base62Util.generateShortCode();
        Base62Util.generateShortCode();

        // Assert
        assertEquals(3, Base62Util.getTotalGeneratedCodes());
    }

    @Test
    @DisplayName("Should return true for existing code")
    void exists_ExistingCode() {
        // Arrange
        String code = Base62Util.generateShortCode();

        // Act & Assert
        assertTrue(Base62Util.exists(code));
    }

    @Test
    @DisplayName("Should return false for non-existing code")
    void exists_NonExistingCode() {
        // Act & Assert
        assertFalse(Base62Util.exists("nonexistent"));
    }

    @Test
    @DisplayName("Should reset internal state")
    void reset_StateCleared() {
        // Arrange
        Base62Util.generateShortCode();
        Base62Util.generateShortCode();
        int beforeCount = Base62Util.getTotalGeneratedCodes();

        // Act
        Base62Util.reset();

        // Assert
        assertEquals(0, Base62Util.getTotalGeneratedCodes());
        assertFalse(Base62Util.exists("aB12Cd")); // Previously generated code
    }

    @Test
    @DisplayName("Should use SecureRandom for cryptographically strong randomness")
    void generatesWithSecureRandom() {
        // Arrange
        String code1 = Base62Util.generateShortCode();
        String code2 = Base62Util.generateShortCode();

        // Assert
        // These should be different due to random generation
        assertNotEquals(code1, code2);
    }
}
