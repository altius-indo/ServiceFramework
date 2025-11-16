package com.enterprise.framework.integration

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.client.WebClient
import io.vertx.kotlin.coroutines.coAwait
import kotlinx.coroutines.delay

class ApiIntegrationTest : DescribeSpec({

    lateinit var vertx: Vertx
    lateinit var client: WebClient
    val baseUrl = "localhost"
    val port = 8080

    beforeSpec {
        vertx = Vertx.vertx()
        client = WebClient.create(vertx)
        // Give server time to start
        delay(2000)
    }

    afterSpec {
        client.close()
        vertx.close().coAwait()
    }

    describe("API v1 Endpoints") {

        describe("GET /api/v1/") {

            it("should reject request without authentication") {
                val response = client.get(port, baseUrl, "/api/v1/")
                    .send()
                    .coAwait()

                response.statusCode() shouldBe 401
            }

            it("should reject request with invalid token") {
                val response = client.get(port, baseUrl, "/api/v1/")
                    .putHeader("Authorization", "Bearer invalid-token")
                    .send()
                    .coAwait()

                response.statusCode() shouldBe 401
            }
        }

        describe("POST /api/v1/") {

            it("should reject create request without authentication") {
                val requestBody = JsonObject()
                    .put("name", "Test Resource")
                    .put("description", "Test Description")

                val response = client.post(port, baseUrl, "/api/v1/")
                    .putHeader("Content-Type", "application/json")
                    .sendJsonObject(requestBody)
                    .coAwait()

                response.statusCode() shouldBe 401
            }

            it("should reject create request with invalid token") {
                val requestBody = JsonObject()
                    .put("name", "Test Resource")
                    .put("description", "Test Description")

                val response = client.post(port, baseUrl, "/api/v1/")
                    .putHeader("Content-Type", "application/json")
                    .putHeader("Authorization", "Bearer invalid-token")
                    .sendJsonObject(requestBody)
                    .coAwait()

                response.statusCode() shouldBe 401
            }
        }

        describe("GET /api/v1/:id") {

            it("should reject get request without authentication") {
                val response = client.get(port, baseUrl, "/api/v1/test-id")
                    .send()
                    .coAwait()

                response.statusCode() shouldBe 401
            }

            it("should reject get request with invalid token") {
                val response = client.get(port, baseUrl, "/api/v1/test-id")
                    .putHeader("Authorization", "Bearer invalid-token")
                    .send()
                    .coAwait()

                response.statusCode() shouldBe 401
            }
        }

        describe("PUT /api/v1/:id") {

            it("should reject update request without authentication") {
                val requestBody = JsonObject()
                    .put("name", "Updated Resource")
                    .put("description", "Updated Description")

                val response = client.put(port, baseUrl, "/api/v1/test-id")
                    .putHeader("Content-Type", "application/json")
                    .sendJsonObject(requestBody)
                    .coAwait()

                response.statusCode() shouldBe 401
            }

            it("should reject update request with invalid token") {
                val requestBody = JsonObject()
                    .put("name", "Updated Resource")
                    .put("description", "Updated Description")

                val response = client.put(port, baseUrl, "/api/v1/test-id")
                    .putHeader("Content-Type", "application/json")
                    .putHeader("Authorization", "Bearer invalid-token")
                    .sendJsonObject(requestBody)
                    .coAwait()

                response.statusCode() shouldBe 401
            }
        }

        describe("DELETE /api/v1/:id") {

            it("should reject delete request without authentication") {
                val response = client.delete(port, baseUrl, "/api/v1/test-id")
                    .send()
                    .coAwait()

                response.statusCode() shouldBe 401
            }

            it("should reject delete request with invalid token") {
                val response = client.delete(port, baseUrl, "/api/v1/test-id")
                    .putHeader("Authorization", "Bearer invalid-token")
                    .send()
                    .coAwait()

                response.statusCode() shouldBe 401
            }
        }

        describe("404 handling") {

            it("should return 404 for unknown endpoints") {
                val response = client.get(port, baseUrl, "/unknown/endpoint")
                    .send()
                    .coAwait()

                response.statusCode() shouldBe 404
                val body = response.bodyAsJsonObject()
                body.getString("error") shouldBe "Not Found"
                body.getString("path") shouldBe "/unknown/endpoint"
            }
        }
    }
})
