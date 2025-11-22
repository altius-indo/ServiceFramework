package com.enterprise.framework.model

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey
import java.time.Instant
import java.util.UUID

/**
 * Represents an audit log entry in the system.
 */
@DynamoDbBean
data class AuditLog(
    var id: String = UUID.randomUUID().toString(),
    var timestamp: Instant = Instant.now(),
    var eventType: String = "",
    var userId: String? = null,
    var username: String? = null,
    var ipAddress: String? = null,
    var userAgent: String? = null,
    var message: String = "",
    var additionalData: Map<String, String> = emptyMap()
) {
    @get:DynamoDbPartitionKey
    var pk: String = "AUDIT#$id"

    @get:DynamoDbSortKey
    var sk: String = "ENTRY"

    // No-arg constructor for DynamoDB
    constructor() : this(id = UUID.randomUUID().toString())
}
