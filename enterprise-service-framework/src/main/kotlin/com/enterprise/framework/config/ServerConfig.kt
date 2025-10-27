package com.enterprise.framework.config

/**
 * Server configuration
 */
data class ServerConfig(
    val host: String = "0.0.0.0",
    val port: Int = 8080,
    val ssl: Boolean = false
)
