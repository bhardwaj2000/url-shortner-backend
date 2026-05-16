package com.mks.open.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for creating a shortened URL.
 * <p>
 * Immutable record class containing the original URL for shortening.
 *
 * @param originalUrl the original URL to be shortened (must be valid)
 */
public record UrlRequestDto(
        @NotBlank(message = "Original URL must not be blank")
        String originalUrl
) {
}
