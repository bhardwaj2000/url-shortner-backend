package com.mks.open.exception;

/**
 * Exception thrown for validation errors.
 * <p>
 * This exception is used to wrap validation failures from Jakarta Validation.
 *
 * @author DevTeam
 */
public class ValidationException extends RuntimeException {

    /**
     * Creates a new ValidationException with the given message.
     *
     * @param message the exception message
     */
    public ValidationException(String message) {
        super(message);
    }

    /**
     * Creates a new ValidationException with the given message and cause.
     *
     * @param message the exception message
     * @param cause   the cause of the exception
     */
    public ValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
