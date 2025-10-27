package com.enterprise.framework.config

/**
 * Database configuration
 */
data class DatabaseConfig(
    val dynamoDbEndpoint: String,
    val region: String,
    val tableName: String
)
