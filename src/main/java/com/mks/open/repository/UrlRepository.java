package com.mks.open.repository;

import com.mks.open.entity.UrlEntity;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * MongoDB repository for URL entity operations.
 * <p>
 * Provides data access operations for URL shortener functionality.
 * <p>
 * Thread-Safety: This repository uses MongoDB's native atomic operations
 * to ensure thread-safe concurrent updates.
 *
 * @author DevTeam
 */
@Repository
public interface UrlRepository extends MongoRepository<UrlEntity, String> {

    /**
     * Finds a URL entity by its short code.
     *
     * @param shortCode the short code to search for
     * @return an Optional containing the found entity, or empty if not found
     */
    Optional<UrlEntity> findByShortCode(String shortCode);

     /**
      * Finds a URL entity by its original URL.
      *
      * @param originalUrl the original URL to search for
      * @return an Optional containing the found entity, or empty if not found
      */
     Optional<UrlEntity> findByOriginalUrl(String originalUrl);

     /**
      * Checks if a short code already exists in the database.
      *
      * @param shortCode the short code to check
      * @return true if the code exists, false otherwise
      */
     boolean existsByShortCode(String shortCode);

     /**
      * Updates the click count atomically using a custom query.
     * <p>
     * This method uses MongoDB's $inc operator to atomically increment
     * the click count without race conditions.
     *
     * @param shortCode the short code of the URL to update
     * @param increment the amount to increment (can be negative)
     * @return the number of documents modified
     */
    @Query("{'shortCode': ?0}")
    @Update("{ $inc: { 'clickCount': ?1 } }")
    long incrementClickCount(@Param("shortCode") String shortCode, @Param("increment") int increment);
}
