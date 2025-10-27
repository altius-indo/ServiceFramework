package com.enterprise.framework.handler

import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.RoutingContext
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * API handler for CRUD operations
 */
class ApiHandler {

    fun handleList(ctx: RoutingContext) {
        logger.info { "Handling list request" }

        val response = JsonObject()
            .put("items", JsonArray())
            .put("total", 0)

        ctx.response()
            .setStatusCode(200)
            .putHeader("content-type", "application/json")
            .end(response.encode())
    }

    fun handleGet(ctx: RoutingContext) {
        val id = ctx.pathParam("id")
        logger.info { "Handling get request for id: $id" }

        val response = JsonObject()
            .put("id", id)
            .put("message", "Item retrieved successfully")

        ctx.response()
            .setStatusCode(200)
            .putHeader("content-type", "application/json")
            .end(response.encode())
    }

    fun handleCreate(ctx: RoutingContext) {
        logger.info { "Handling create request" }

        val body = ctx.body().asJsonObject()
        logger.debug { "Request body: ${body?.encodePrettily()}" }

        val response = JsonObject()
            .put("id", java.util.UUID.randomUUID().toString())
            .put("message", "Item created successfully")
            .put("data", body)

        ctx.response()
            .setStatusCode(201)
            .putHeader("content-type", "application/json")
            .end(response.encode())
    }

    fun handleUpdate(ctx: RoutingContext) {
        val id = ctx.pathParam("id")
        logger.info { "Handling update request for id: $id" }

        val body = ctx.body().asJsonObject()
        logger.debug { "Request body: ${body?.encodePrettily()}" }

        val response = JsonObject()
            .put("id", id)
            .put("message", "Item updated successfully")
            .put("data", body)

        ctx.response()
            .setStatusCode(200)
            .putHeader("content-type", "application/json")
            .end(response.encode())
    }

    fun handleDelete(ctx: RoutingContext) {
        val id = ctx.pathParam("id")
        logger.info { "Handling delete request for id: $id" }

        val response = JsonObject()
            .put("id", id)
            .put("message", "Item deleted successfully")

        ctx.response()
            .setStatusCode(200)
            .putHeader("content-type", "application/json")
            .end(response.encode())
    }
}
