package com.enterprise.framework.config

/**
 * Redis configuration
 */
data class RedisConfig(
    val host: String,
    val port: Int,
    val password: String?
)
