package com.enterprise.framework.integration

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldNotBeEmpty
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.client.WebClient
import io.vertx.kotlin.coroutines.coAwait
import kotlinx.coroutines.delay

class AuthenticationIntegrationTest : DescribeSpec({

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

    describe("Authentication Endpoints") {

        describe("POST /auth/login") {

            it("should reject login with missing username") {
                val requestBody = JsonObject()
                    .put("password", "TestPassword123!")

                val response = client.post(port, baseUrl, "/auth/login")
                    .putHeader("Content-Type", "application/json")
                    .sendJsonObject(requestBody)
                    .coAwait()

                response.statusCode() shouldBe 400
            }

            it("should reject login with missing password") {
                val requestBody = JsonObject()
                    .put("username", "testuser")

                val response = client.post(port, baseUrl, "/auth/login")
                    .putHeader("Content-Type", "application/json")
                    .sendJsonObject(requestBody)
                    .coAwait()

                response.statusCode() shouldBe 400
            }

            it("should reject login with empty credentials") {
                val requestBody = JsonObject()
                    .put("username", "")
                    .put("password", "")

                val response = client.post(port, baseUrl, "/auth/login")
                    .putHeader("Content-Type", "application/json")
                    .sendJsonObject(requestBody)
                    .coAwait()

                response.statusCode() shouldBe 400
            }

            it("should reject login with non-existent user") {
                val requestBody = JsonObject()
                    .put("username", "nonexistentuser")
                    .put("password", "TestPassword123!")

                val response = client.post(port, baseUrl, "/auth/login")
                    .putHeader("Content-Type", "application/json")
                    .sendJsonObject(requestBody)
                    .coAwait()

                response.statusCode() shouldBe 401
            }
        }

        describe("POST /auth/refresh") {

            it("should reject refresh with missing token") {
                val requestBody = JsonObject()

                val response = client.post(port, baseUrl, "/auth/refresh")
                    .putHeader("Content-Type", "application/json")
                    .sendJsonObject(requestBody)
                    .coAwait()

                response.statusCode() shouldBe 400
            }

            it("should reject refresh with invalid token") {
                val requestBody = JsonObject()
                    .put("refreshToken", "invalid-token")

                val response = client.post(port, baseUrl, "/auth/refresh")
                    .putHeader("Content-Type", "application/json")
                    .sendJsonObject(requestBody)
                    .coAwait()

                response.statusCode() shouldBe 401
            }
        }

        describe("POST /auth/introspect") {

            it("should reject introspection with missing token") {
                val requestBody = JsonObject()

                val response = client.post(port, baseUrl, "/auth/introspect")
                    .putHeader("Content-Type", "application/json")
                    .sendJsonObject(requestBody)
                    .coAwait()

                response.statusCode() shouldBe 400
            }

            it("should return active false for invalid token") {
                val requestBody = JsonObject()
                    .put("token", "invalid-token")

                val response = client.post(port, baseUrl, "/auth/introspect")
                    .putHeader("Content-Type", "application/json")
                    .sendJsonObject(requestBody)
                    .coAwait()

                response.statusCode() shouldBe 200
                val body = response.bodyAsJsonObject()
                body.getBoolean("active") shouldBe false
            }
        }

        describe("POST /auth/logout") {

            it("should reject logout without authentication") {
                val response = client.post(port, baseUrl, "/auth/logout")
                    .putHeader("Content-Type", "application/json")
                    .send()
                    .coAwait()

                response.statusCode() shouldBe 401
            }

            it("should reject logout with invalid token") {
                val response = client.post(port, baseUrl, "/auth/logout")
                    .putHeader("Content-Type", "application/json")
                    .putHeader("Authorization", "Bearer invalid-token")
                    .send()
                    .coAwait()

                response.statusCode() shouldBe 401
            }
        }

        describe("GET /auth/sessions") {

            it("should reject sessions list without authentication") {
                val response = client.get(port, baseUrl, "/auth/sessions")
                    .send()
                    .coAwait()

                response.statusCode() shouldBe 401
            }

            it("should reject sessions list with invalid token") {
                val response = client.get(port, baseUrl, "/auth/sessions")
                    .putHeader("Authorization", "Bearer invalid-token")
                    .send()
                    .coAwait()

                response.statusCode() shouldBe 401
            }
        }

        describe("DELETE /auth/sessions/:sessionId") {

            it("should reject session termination without authentication") {
                val response = client.delete(port, baseUrl, "/auth/sessions/test-session-id")
                    .send()
                    .coAwait()

                response.statusCode() shouldBe 401
            }

            it("should reject session termination with invalid token") {
                val response = client.delete(port, baseUrl, "/auth/sessions/test-session-id")
                    .putHeader("Authorization", "Bearer invalid-token")
                    .send()
                    .coAwait()

                response.statusCode() shouldBe 401
            }
        }
    }
})
