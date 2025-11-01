package com.enterprise.framework.repository

import com.enterprise.framework.config.DatabaseConfig
import mu.KotlinLogging
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable
import software.amazon.awssdk.enhanced.dynamodb.TableSchema
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import java.net.URI

private val logger = KotlinLogging.logger {}

/**
 * A generic repository for interacting with a DynamoDB table.
 *
 * This class provides a set of common methods for performing CRUD (Create, Read,
 * Update, Delete) operations on a DynamoDB table. It is designed to be
 * type-safe and reusable for different data models.
 *
 * @param T The type of the data entity that this repository manages.
 * @property config The database configuration settings.
 * @property tableSchema The schema definition for the DynamoDB table.
 * @property entityClass The class of the entity, used for logging purposes.
 */
open class DynamoDbRepository<T>(
    private val config: DatabaseConfig,
    private val tableSchema: TableSchema<T>,
    private val entityClass: Class<T>
) {

    private val dynamoDbClient: DynamoDbClient
    private val enhancedClient: DynamoDbEnhancedClient
    private val table: DynamoDbTable<T>

    init {
        logger.info { "Initializing DynamoDB repository for ${entityClass.simpleName}" }

        dynamoDbClient = DynamoDbClient.builder()
            .region(Region.of(config.region))
            .endpointOverride(URI.create(config.dynamoDbEndpoint))
            .credentialsProvider(DefaultCredentialsProvider.create())
            .build()

        enhancedClient = DynamoDbEnhancedClient.builder()
            .dynamoDbClient(dynamoDbClient)
            .build()

        table = enhancedClient.table(config.tableName, tableSchema)
    }

    /**
     * Saves an item to the DynamoDB table.
     *
     * If an item with the same primary key already exists, it will be overwritten.
     *
     * @param item The item to be saved.
     */
    fun save(item: T) {
        logger.debug { "Saving item to DynamoDB: $item" }
        table.putItem(item)
    }

    /**
     * Retrieves an item from the DynamoDB table by its key.
     *
     * @param key The key of the item to retrieve.
     * @return The retrieved item, or `null` if no item is found with the given key.
     */
    fun get(key: T): T? {
        logger.debug { "Getting item from DynamoDB with key: $key" }
        return table.getItem(key)
    }

    /**
     * Deletes an item from the DynamoDB table by its key.
     *
     * @param key The key of the item to delete.
     */
    fun delete(key: T) {
        logger.debug { "Deleting item from DynamoDB with key: $key" }
        table.deleteItem(key)
    }

    /**
     * Updates an existing item in the DynamoDB table.
     *
     * @param item The item with updated values.
     * @return The updated item as returned by DynamoDB.
     */
    fun update(item: T): T {
        logger.debug { "Updating item in DynamoDB: $item" }
        return table.updateItem(item)
    }

    /**
     * Closes the underlying DynamoDB client.
     *
     * This method should be called when the repository is no longer needed to
     * release the resources used by the client.
     */
    fun close() {
        logger.info { "Closing DynamoDB client" }
        dynamoDbClient.close()
    }
}
