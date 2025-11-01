package com.enterprise.framework.handler

import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.ext.healthchecks.HealthCheckHandler
import io.vertx.ext.healthchecks.Status
import io.vertx.ext.web.RoutingContext
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * A handler for managing application health checks, including readiness and liveness probes.
 *
 * This class sets up and exposes health check endpoints that can be used by monitoring
 * systems to determine the status of the application and its dependencies. It
 * provides handlers for overall health, readiness, and liveness.
 *
 * @property vertx The Vert.x instance used for creating the health check handler.
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

    /**
     * Handles a general health check request.
     *
     * This method delegates to the Vert.x [HealthCheckHandler], which executes
     * all registered health checks and returns a consolidated status.
     *
     * @param ctx The [RoutingContext] of the request.
     */
    fun handle(ctx: RoutingContext) {
        healthCheckHandler.handle(ctx)
    }

    /**
     * Handles a readiness probe request.
     *
     * This method is intended for use by orchestrators like Kubernetes to determine
     * if the application is ready to accept traffic. It returns a simple "UP"
     * status.
     *
     * @param ctx The [RoutingContext] of the request.
     */
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

    /**
     * Handles a liveness probe request.
     *
     * This method is used by orchestrators to determine if the application is
     * still running. A successful response indicates that the application has
     * not crashed or entered a deadlocked state.
     *
     * @param ctx The [RoutingContext] of the request.
     */
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
