package com.enterprise.framework.handler

import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.ext.healthchecks.HealthCheckHandler
import io.vertx.ext.healthchecks.Status
import io.vertx.ext.web.RoutingContext
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Health check handler for application monitoring
 */
class HealthCheckHandler(private val vertx: Vertx) {

    private val healthCheckHandler = HealthCheckHandler.create(vertx)

    init {
        setupHealthChecks()
    }

    private fun setupHealthChecks() {
        healthCheckHandler.register("database") { promise ->
            try {
                promise.complete(Status.OK())
            } catch (e: Exception) {
                logger.error(e) { "Database health check failed" }
                promise.complete(Status.KO())
            }
        }

        healthCheckHandler.register("redis") { promise ->
            try {
                promise.complete(Status.OK())
            } catch (e: Exception) {
                logger.error(e) { "Redis health check failed" }
                promise.complete(Status.KO())
            }
        }
    }

    fun handle(ctx: RoutingContext) {
        healthCheckHandler.handle(ctx)
    }

    fun handleReadiness(ctx: RoutingContext) {
        val response = JsonObject()
            .put("status", "UP")
            .put("checks", JsonObject()
                .put("readiness", "UP")
            )

        ctx.response()
            .setStatusCode(200)
            .putHeader("content-type", "application/json")
            .end(response.encode())
    }

    fun handleLiveness(ctx: RoutingContext) {
        val response = JsonObject()
            .put("status", "UP")
            .put("checks", JsonObject()
                .put("liveness", "UP")
            )

        ctx.response()
            .setStatusCode(200)
            .putHeader("content-type", "application/json")
            .end(response.encode())
    }
}
