package com.enterprise.framework.verticle

import io.vertx.core.json.JsonObject
import io.vertx.kotlin.coroutines.CoroutineVerticle
import mu.KotlinLogging

/**
 * An abstract base class for all verticles in the application.
 *
 * This class provides a common structure for verticles, including standardized
 * logging and a lifecycle management pattern with `initialize` and `cleanup`
 * methods. Subclasses should implement the `initialize` method and can
 * optionally override the `cleanup` method.
 */
abstract class BaseVerticle : CoroutineVerticle() {

    protected val logger = KotlinLogging.logger {}

    /**
     * Called when the verticle is deployed.
     *
     * This method orchestrates the startup of the verticle by calling the
     * abstract `initialize` method. It also provides centralized logging for
     * the startup process.
     */
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

    /**
     * Called when the verticle is undeployed.
     *
     * This method orchestrates the shutdown of the verticle by calling the
     * `cleanup` method. It provides centralized logging for the shutdown process.
     */
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
     * Abstract method to be implemented by subclasses for their initialization logic.
     *
     * This method is called during the verticle's startup sequence and should
     * contain the primary setup logic for the verticle.
     */
    protected abstract suspend fun initialize()

    /**
     * Can be overridden by subclasses to perform cleanup tasks.
     *
     * This method is called during the verticle's shutdown sequence. The default
     * implementation does nothing.
     */
    protected open suspend fun cleanup() {
        // Default implementation does nothing
    }
}
