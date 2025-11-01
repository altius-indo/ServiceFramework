package com.enterprise.framework.config

/**
 * Represents the database configuration for the application.
 *
 * This data class holds all the necessary settings for connecting to and
 * interacting with the DynamoDB database.
 *
 * @property dynamoDbEndpoint The endpoint URL for the DynamoDB service.
 * @property region The AWS region where the DynamoDB instance is located.
 * @property tableName The name of the DynamoDB table used by the application.
 */
data class DatabaseConfig(
    val dynamoDbEndpoint: String,
    val region: String,
    val tableName: String
)
