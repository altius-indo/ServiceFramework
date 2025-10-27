package com.enterprise.framework

import com.enterprise.framework.config.ConfigLoader
import com.enterprise.framework.verticle.HttpServerVerticle
import io.vertx.core.DeploymentOptions
import io.vertx.core.Vertx
import io.vertx.core.VertxOptions
import io.vertx.core.json.JsonObject
import io.vertx.kotlin.coroutines.await
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

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

            // Deploy HTTP Server Verticle
            val httpServerDeploymentOptions = DeploymentOptions()
                .setConfig(config)
                .setInstances(Runtime.getRuntime().availableProcessors())

            val httpServerId = vertx.deployVerticle(
                HttpServerVerticle::class.java.name,
                httpServerDeploymentOptions
            ).await()

            logger.info { "HTTP Server Verticle deployed: $httpServerId" }

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
