package com.mks.open.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Indexes;

/**
 * Application configuration class.
 * <p>
 * Contains configuration for Spring Boot, MongoDB, and other components.
 * <p>
 * Thread-Safety & Concurrency:
 * This configuration ensures:
 * <ul>
 *   <li>Unique index on shortCode prevents duplicate short codes across concurrent requests</li>
 *   <li>Index on originalUrl enables fast duplicate detection for idempotency</li>
 *   <li>MongoDB ensures atomicity for all index operations</li>
 * </ul>
 *
 * @author DevTeam
 */
@Configuration
@EnableMongoRepositories(basePackages = "com.mks.open.repository")
public class AppConfig {

    private static final Logger log = LoggerFactory.getLogger(AppConfig.class);

    private static final String SHORT_CODE_INDEX_NAME = "idx_shortcode_unique";
    private static final String ORIGINAL_URL_INDEX_NAME = "idx_originalurl";
    private static final String CLICK_COUNT_INDEX_NAME = "idx_clickcount";
    private static final String CREATED_AT_INDEX_NAME = "idx_createdat";

    /**
     * Post-initializes MongoDB indexes after the application starts.
     * <p>
     * Creates indexes on shortCode (unique), originalUrl, clickCount, and createdAt
     * to optimize query performance and prevent duplicates.
     * <p>
     * Indexes created:
     * <ul>
     *   <li>shortCode (UNIQUE): Prevents duplicate short codes and enables O(1) lookups</li>
     *   <li>originalUrl: Enables O(1) duplicate URL detection for idempotency</li>
     *   <li>clickCount: Allows analytics queries and sorting by popularity</li>
     *   <li>createdAt: Enables time-based sorting and filtering</li>
     * </ul>
     *
     * @param mongoClient the MongoDB client
     * @param databaseName the database name
     */
    public void postInitializeMongoIndexes(MongoClient mongoClient, String databaseName) {
        try {
            MongoDatabase database = mongoClient.getDatabase(databaseName);

            // Create collection if it doesn't exist (use Spring default name "shortner")
            try {
                database.createCollection("shortner", new com.mongodb.client.model.CreateCollectionOptions());
                log.info("Created collection 'shortner'");
            } catch (Exception ignored) {
                // Collection already exists
            }

            var collection = database.getCollection("shortner");

            // Create unique index on shortCode for collision prevention and fast lookup
            createIndexIfNotExists(collection, "shortCode", SHORT_CODE_INDEX_NAME, true);

            // Create unique index on originalUrl for duplicate detection (idempotency)
            createIndexIfNotExists(collection, "originalUrl", ORIGINAL_URL_INDEX_NAME, true);

            // Create index on clickCount for analytics queries
            createIndexIfNotExists(collection, "clickCount", CLICK_COUNT_INDEX_NAME, false);

            // Create index on createdAt for time-based queries and sorting
            createIndexIfNotExists(collection, "createdAt", CREATED_AT_INDEX_NAME, false);

            log.info("Successfully initialized all MongoDB indexes");
        } catch (Exception e) {
            log.error("Failed to initialize indexes: {}", e.getMessage(), e);
        }
    }

    /**
     * Creates an index on a specified field if it doesn't already exist.
     *
     * @param collection the MongoDB collection
     * @param fieldName  the field to create index on
     * @param indexName  the name of the index
     * @param unique     whether the index should be unique
     */
    private void createIndexIfNotExists(
            com.mongodb.client.MongoCollection<org.bson.Document> collection,
            String fieldName,
            String indexName,
            boolean unique) {
        try {
            // Check if index already exists
            boolean exists = false;
            for (var indexDoc : collection.listIndexes()) {
                if (indexName.equals(indexDoc.getString("name"))) {
                    exists = true;
                    log.debug("Index {} already exists", indexName);
                    break;
                }
            }

            // Create index if it doesn't exist
            if (!exists) {
                var indexOptions = new com.mongodb.client.model.IndexOptions()
                        .name(indexName)
                        .background(true);

                if (unique) {
                    indexOptions.unique(true);
                }

                collection.createIndex(
                        Indexes.ascending(fieldName),
                        indexOptions
                );
                log.info("Created {} index on field '{}': {}", 
                        unique ? "unique" : "regular", fieldName, indexName);
            }
        } catch (Exception e) {
            log.warn("Failed to create index {}: {}", indexName, e.getMessage());
        }
    }
}
