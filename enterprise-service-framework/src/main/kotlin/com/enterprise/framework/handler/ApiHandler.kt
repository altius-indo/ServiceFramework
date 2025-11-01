package com.enterprise.framework.handler

import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.RoutingContext
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * A handler for processing common API requests, such as CRUD (Create, Read, Update, Delete) operations.
 *
 * This class provides a set of methods for handling standard RESTful API endpoints.
 * Each method is designed to be used as a handler in a Vert.x web application and
 * is responsible for processing a specific type of request and sending an appropriate
 * JSON response.
 */
class ApiHandler {

    /**
     * Handles a request to list items.
     *
     * This method sends a JSON response containing a list of items and a total count.
     * In this default implementation, the list is empty.
     *
     * @param ctx The [RoutingContext] of the request.
     */
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

    /**
     * Handles a request to retrieve a single item by its ID.
     *
     * This method extracts the item's ID from the request path and sends a JSON
     * response containing the ID and a success message.
     *
     * @param ctx The [RoutingContext] of the request.
     */
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

    /**
     * Handles a request to create a new item.
     *
     * This method processes the JSON body of the request and sends a response
     * indicating that the item was created successfully. It includes a newly
     * generated UUID for the created item.
     *
     * @param ctx The [RoutingContext] of the request.
     */
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

    /**
     * Handles a request to update an existing item.
     *
     * This method extracts the item's ID from the request path and processes the
     * JSON body of the request. It sends a response indicating that the update
     * was successful.
     *
     * @param ctx The [RoutingContext] of the request.
     */
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

    /**
     * Handles a request to delete an item by its ID.
     *
     * This method extracts the item's ID from the request path and sends a JSON
     * response confirming that the item has been deleted.
     *
     * @param ctx The [RoutingContext] of the request.
     */
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
