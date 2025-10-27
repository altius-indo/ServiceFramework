package com.enterprise.framework.verticle

import com.enterprise.framework.handler.ApiHandler
import com.enterprise.framework.handler.HealthCheckHandler
import io.vertx.ext.web.Router
import io.vertx.ext.web.handler.BodyHandler
import io.vertx.ext.web.handler.CorsHandler
import io.vertx.ext.web.handler.LoggerHandler
import io.vertx.ext.web.handler.TimeoutHandler
import io.vertx.kotlin.coroutines.await

/**
 * HTTP Server Verticle
 * Handles all HTTP requests and routing
 */
class HttpServerVerticle : BaseVerticle() {

    private lateinit var router: Router

    override suspend fun initialize() {
        router = Router.router(vertx)

        setupMiddleware()
        setupRoutes()
        startHttpServer()
    }

    private fun setupMiddleware() {
        logger.info { "Setting up middleware..." }

        router.route().handler(LoggerHandler.create())
        router.route().handler(BodyHandler.create())
        router.route().handler(TimeoutHandler.create(30000))

        router.route().handler(CorsHandler.create()
            .addOrigin("*")
            .allowedMethod(io.vertx.core.http.HttpMethod.GET)
            .allowedMethod(io.vertx.core.http.HttpMethod.POST)
            .allowedMethod(io.vertx.core.http.HttpMethod.PUT)
            .allowedMethod(io.vertx.core.http.HttpMethod.DELETE)
            .allowedMethod(io.vertx.core.http.HttpMethod.PATCH)
            .allowedMethod(io.vertx.core.http.HttpMethod.OPTIONS)
            .allowedHeader("Content-Type")
            .allowedHeader("Authorization")
        )
    }

    private fun setupRoutes() {
        logger.info { "Setting up routes..." }

        val healthCheckHandler = HealthCheckHandler(vertx)
        val apiHandler = ApiHandler()

        router.get("/health").handler(healthCheckHandler::handle)
        router.get("/ready").handler(healthCheckHandler::handleReadiness)
        router.get("/live").handler(healthCheckHandler::handleLiveness)

        val apiRouter = Router.router(vertx)
        apiRouter.get("/").handler(apiHandler::handleList)
        apiRouter.post("/").handler(apiHandler::handleCreate)
        apiRouter.get("/:id").handler(apiHandler::handleGet)
        apiRouter.put("/:id").handler(apiHandler::handleUpdate)
        apiRouter.delete("/:id").handler(apiHandler::handleDelete)

        router.route("/api/v1/*").subRouter(apiRouter)

        router.route().last().handler { ctx ->
            ctx.response()
                .setStatusCode(404)
                .putHeader("content-type", "application/json")
                .end("""{"error": "Not Found", "path": "${ctx.request().path()}"}""")
        }
    }

    private suspend fun startHttpServer() {
        val serverConfig = config.getJsonObject("server")
        val host = serverConfig?.getString("host") ?: "0.0.0.0"
        val port = serverConfig?.getInteger("port") ?: 8080

        vertx.createHttpServer()
            .requestHandler(router)
            .listen(port, host)
            .await()

        logger.info { "HTTP server started on $host:$port" }
    }

    override suspend fun cleanup() {
        logger.info { "Cleaning up HTTP server..." }
    }
}
