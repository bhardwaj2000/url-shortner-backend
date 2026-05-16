package com.mks.open.dto;

import java.time.Instant;

/**
 * Error response DTO for API errors.
 * <p>
 * Immutable record class containing error information.
 *
 * @param timestamp the timestamp when the error occurred
 * @param status    the HTTP status code
 * @param error     the error type (e.g., "Bad Request", "Not Found")
 * @param message   the error message
 * @param path      the request path that caused the error
 */
public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path
) {
    /**
     * Creates a new ErrorResponse builder.
     *
     * @return a new ErrorResponse instance
     */
    public static ErrorResponseBuilder builder() {
        return new ErrorResponseBuilder();
    }

    /**
     * Builder class for ErrorResponse.
     */
    public static class ErrorResponseBuilder {
        private Instant timestamp;
        private int status;
        private String error;
        private String message;
        private String path;

        public ErrorResponseBuilder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public ErrorResponseBuilder status(int status) {
            this.status = status;
            return this;
        }

        public ErrorResponseBuilder error(String error) {
            this.error = error;
            return this;
        }

        public ErrorResponseBuilder message(String message) {
            this.message = message;
            return this;
        }

        public ErrorResponseBuilder path(String path) {
            this.path = path;
            return this;
        }

        public ErrorResponse build() {
            return new ErrorResponse(timestamp, status, error, message, path);
        }
    }

    /**
     * Creates a new ErrorResponse with the current timestamp.
     *
     * @param status  the HTTP status code
     * @param error   the error type
     * @param message the error message
     * @param path    the request path
     * @return a new ErrorResponse instance
     */
    public static ErrorResponse of(int status, String error, String message, String path) {
        return new ErrorResponse(Instant.now(), status, error, message, path);
    }
}
