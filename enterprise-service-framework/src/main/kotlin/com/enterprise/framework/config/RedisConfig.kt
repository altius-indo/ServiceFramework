package com.enterprise.framework.config

/**
 * Represents the Redis configuration for the application.
 *
 * This data class holds all the necessary settings for connecting to the
 * Redis instance, which is used for caching and session management.
 *
 * @property host The hostname or IP address of the Redis server.
 * @property port The port number on which the Redis server is listening.
 * @property password The password for authenticating with the Redis server, if required.
 */
data class RedisConfig(
    val host: String,
    val port: Int,
    val password: String?
)
