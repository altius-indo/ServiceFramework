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
 * DynamoDB repository for database operations
 */
class DynamoDbRepository<T>(
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

    fun save(item: T) {
        logger.debug { "Saving item to DynamoDB: $item" }
        table.putItem(item)
    }

    fun get(key: T): T? {
        logger.debug { "Getting item from DynamoDB with key: $key" }
        return table.getItem(key)
    }

    fun delete(key: T) {
        logger.debug { "Deleting item from DynamoDB with key: $key" }
        table.deleteItem(key)
    }

    fun update(item: T): T {
        logger.debug { "Updating item in DynamoDB: $item" }
        return table.updateItem(item)
    }

    fun close() {
        logger.info { "Closing DynamoDB client" }
        dynamoDbClient.close()
    }
}
