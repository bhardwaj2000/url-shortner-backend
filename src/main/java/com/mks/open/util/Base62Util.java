package com.mks.open.util;

import java.security.SecureRandom;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Utility class for generating Base62 encoded short codes.
 * <p>
 * Base62 uses characters: a-z, A-Z, 0-9 (62 total characters)
 * <p>
 * Thread-Safety: This class is thread-safe for concurrent use.
 * - Uses {@link SecureRandom} which is thread-safe
 * - Uses {@link ConcurrentHashMap} to track generated codes
 * - Uses {@link AtomicInteger} for retry counting
 * <p>
 * Collision Handling:
 * - Maintains a thread-safe set of all generated short codes
 * - Retries generation up to MAX_RETRIES on collision
 * - Exponentially increasing retry window on collision
 *
 * @author DevTeam
 */
public class Base62Util {

    /**
     * Base62 character set: a-z (26) + A-Z (26) + 0-9 (10) = 62 characters
     */
    private static final String BASE62_CHARS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    /**
     * Default short code length (6 characters provides ~56.8 billion combinations)
     */
    private static final int DEFAULT_CODE_LENGTH = 6;

    /**
     * Maximum retries before throwing exception
     */
    private static final int MAX_RETRIES = 5;

    /**
     * Thread-safe set to track all generated codes for collision detection
     */
    private static final Set<String> GENERATED_CODES = ConcurrentHashMap.newKeySet();

    /**
     * Thread-safe counter for retry attempts (for metrics)
     */
    private static final AtomicInteger TOTAL_RETRIES = new AtomicInteger(0);

    /**
     * Thread-safe random number generator
     * {@link SecureRandom} is thread-safe and cryptographically strong
     */
    private static final SecureRandom RANDOM = new SecureRandom();

    private Base62Util() {
        throw new AssertionError("Utility class cannot be instantiated");
    }

    /**
     * Generates a unique Base62 short code.
     *
     * @param codeLength the length of the short code (minimum 4, maximum 12)
     * @return a unique Base62 encoded string
     * @throws RuntimeException if unable to generate unique code after max retries
     */
    public static String generateShortCode(int codeLength) {
        if (codeLength < 4 || codeLength > 12) {
            throw new IllegalArgumentException("Code length must be between 4 and 12 characters");
        }

        return generateCodeWithRetries(codeLength, 0);
    }

    /**
     * Generates a short code using default length (6 characters).
     *
     * @return a unique Base62 encoded string
     * @throws RuntimeException if unable to generate unique code after max retries
     */
    public static String generateShortCode() {
        return generateShortCode(DEFAULT_CODE_LENGTH);
    }

    /**
     * Recursively generates a short code with retry logic.
     *
     * @param codeLength the length of the short code
     * @param retryCount current retry count
     * @return a unique Base62 encoded string
     * @throws RuntimeException if max retries exceeded
     */
    private static String generateCodeWithRetries(int codeLength, int retryCount) {
        // Check if max retries exceeded
        if (retryCount >= MAX_RETRIES) {
            throw new RuntimeException(
                    "Unable to generate unique short code after " + retryCount + " retries");
        }

        // Generate a random code
        String code = generateRandomCode(codeLength);

        // Try to add to the set (returns true if added, false if already exists)
        if (GENERATED_CODES.add(code)) {
            return code;
        }

        // Collision detected, increment retry counter and retry
        TOTAL_RETRIES.incrementAndGet();

        // Add a small delay (exponential backoff) to reduce contention
        if (retryCount > 0) {
            sleepRandomly(retryCount);
        }

        return generateCodeWithRetries(codeLength, retryCount + 1);
    }

    /**
     * Generates a single random Base62 code.
     *
     * @param codeLength the length of the code
     * @return a Base62 encoded string
     */
    private static String generateRandomCode(int codeLength) {
        StringBuilder sb = new StringBuilder(codeLength);
        for (int i = 0; i < codeLength; i++) {
            int randomIndex = RANDOM.nextInt(BASE62_CHARS.length());
            sb.append(BASE62_CHARS.charAt(randomIndex));
        }
        return sb.toString();
    }

    /**
     * Sleeps for a random time based on retry count (exponential backoff).
     *
     * @param retryCount current retry count
     */
    private static void sleepRandomly(int retryCount) {
        try {
            // Exponential backoff: 1ms, 2ms, 4ms, 8ms, 16ms
            int delayMs = Math.min(1 << retryCount, 16);
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Checks if a given short code exists in the generated codes set.
     *
     * @param shortCode the code to check
     * @return true if the code exists, false otherwise
     */
    public static boolean exists(String shortCode) {
        return GENERATED_CODES.contains(shortCode);
    }

    /**
     * Gets the total count of all generated short codes.
     *
     * @return the total number of unique codes generated in this JVM instance
     */
    public static int getTotalGeneratedCodes() {
        return GENERATED_CODES.size();
    }

    /**
     * Gets the total number of retry attempts due to collisions.
     *
     * @return the total retry count
     */
    public static int getTotalRetries() {
        return TOTAL_RETRIES.get();
    }

    /**
     * Resets the generated codes set (mainly for testing purposes).
     * <p>
     * WARNING: This should only be used in test environments.
     */
    public static void reset() {
        GENERATED_CODES.clear();
        TOTAL_RETRIES.set(0);
    }
}
