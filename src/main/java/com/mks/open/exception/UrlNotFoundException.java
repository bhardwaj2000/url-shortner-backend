package com.mks.open.exception;

/**
 * Exception thrown when a URL entity is not found.
 * <p>
 * This exception is used to indicate that a requested URL or short code
 * does not exist in the system.
 *
 * @author DevTeam
 */
public class UrlNotFoundException extends RuntimeException {

    /**
     * Creates a new UrlNotFoundException with the given message.
     *
     * @param message the exception message
     */
    public UrlNotFoundException(String message) {
        super(message);
    }

    /**
     * Creates a new UrlNotFoundException with the given message and cause.
     *
     * @param message the exception message
     * @param cause   the cause of the exception
     */
    public UrlNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
