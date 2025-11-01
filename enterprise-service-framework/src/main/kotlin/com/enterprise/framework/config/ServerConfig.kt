package com.enterprise.framework.config

/**
 * Represents the server configuration for the application.
 *
 * This data class holds all the settings related to the HTTP server, such as
 * the host, port, and SSL configuration.
 *
 * @property host The hostname or IP address on which the server will listen.
 * @property port The port number for the server.
 * @property ssl A boolean flag indicating whether SSL/TLS is enabled.
 */
data class ServerConfig(
    val host: String = "0.0.0.0",
    val port: Int = 8080,
    val ssl: Boolean = false
)
