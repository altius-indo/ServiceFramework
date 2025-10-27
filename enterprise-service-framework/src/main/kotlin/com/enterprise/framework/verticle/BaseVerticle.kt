package com.enterprise.framework.verticle

import io.vertx.core.json.JsonObject
import io.vertx.kotlin.coroutines.CoroutineVerticle
import mu.KotlinLogging

/**
 * Base verticle class for all verticles in the framework
 */
abstract class BaseVerticle : CoroutineVerticle() {

    protected val logger = KotlinLogging.logger {}

    override suspend fun start() {
        logger.info { "Starting ${this::class.simpleName}..." }
        try {
            initialize()
            logger.info { "${this::class.simpleName} started successfully" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to start ${this::class.simpleName}" }
            throw e
        }
    }

    override suspend fun stop() {
        logger.info { "Stopping ${this::class.simpleName}..." }
        try {
            cleanup()
            logger.info { "${this::class.simpleName} stopped successfully" }
        } catch (e: Exception) {
            logger.error(e) { "Error stopping ${this::class.simpleName}" }
            throw e
        }
    }

    /**
     * Initialize the verticle
     */
    protected abstract suspend fun initialize()

    /**
     * Cleanup resources before stopping
     */
    protected open suspend fun cleanup() {
        // Default implementation does nothing
    }
}
