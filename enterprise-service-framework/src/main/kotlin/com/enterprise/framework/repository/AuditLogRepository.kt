package com.enterprise.framework.repository

import com.enterprise.framework.config.DatabaseConfig
import com.enterprise.framework.model.AuditLog
import software.amazon.awssdk.enhanced.dynamodb.TableSchema

/**
 * Repository for AuditLog entries.
 * Extends the generic DynamoDbRepository.
 */
class AuditLogRepository(config: DatabaseConfig) : DynamoDbRepository<AuditLog>(
    config,
    TableSchema.fromBean(AuditLog::class.java),
    AuditLog::class.java
)
