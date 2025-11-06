package com.enterprise.framework

import com.enterprise.framework.config.ConfigLoader
import com.enterprise.framework.verticle.EnhancedHttpServerVerticle
import io.vertx.core.DeploymentOptions
import io.vertx.core.Vertx
import io.vertx.core.VertxOptions
import io.vertx.core.json.JsonObject
import io.vertx.kotlin.coroutines.await
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * The main entry point for the Enterprise Service Framework application.
 *
 * This function initializes and configures the Vert.x instance, loads the application
 * configuration, deploys the necessary verticles (such as the HTTP server),
 * and sets up a graceful shutdown hook. The application will continue running
 * until it is terminated.
 *
 * @param args Command-line arguments passed to the application.
 */
fun main(args: Array<String>) {
    runBlocking {
        logger.info { "Starting Enterprise Service Framework..." }

        val vertxOptions = VertxOptions()
            .setWorkerPoolSize(20)
            .setEventLoopPoolSize(Runtime.getRuntime().availableProcessors() * 2)

        val vertx = Vertx.vertx(vertxOptions)

        try {
            // Load configuration
            val configLoader = ConfigLoader(vertx)
            val config = configLoader.loadConfig()

            // Deploy verticles
            logger.info { "Deploying verticles..." }

            // Deploy Enhanced HTTP Server Verticle with Authentication
            val httpServerDeploymentOptions = DeploymentOptions()
                .setConfig(config)
                .setInstances(1) // Single instance due to Redis/DynamoDB initialization

            val httpServerId = vertx.deployVerticle(
                EnhancedHttpServerVerticle::class.java.name,
                httpServerDeploymentOptions
            ).await()

            logger.info { "Enhanced HTTP Server Verticle deployed: $httpServerId" }

            logger.info { "Enterprise Service Framework started successfully" }
            logger.info { "Server is listening on ${config.getJsonObject("server")?.getString("host")}:${config.getJsonObject("server")?.getInteger("port")}" }

            // Add shutdown hook
            Runtime.getRuntime().addShutdownHook(Thread {
                logger.info { "Shutting down Enterprise Service Framework..." }
                runBlocking {
                    try {
                        vertx.close().await()
                        logger.info { "Shutdown complete" }
                    } catch (e: Exception) {
                        logger.error(e) { "Error during shutdown" }
                    }
                }
            })

            // Keep the application running
            Thread.currentThread().join()

        } catch (e: Exception) {
            logger.error(e) { "Failed to start application" }
            vertx.close()
            throw e
        }
    }
}
