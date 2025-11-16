package com.enterprise.framework.integration

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.client.WebClient
import io.vertx.kotlin.coroutines.coAwait
import kotlinx.coroutines.delay

class HealthCheckIntegrationTest : DescribeSpec({

    lateinit var vertx: Vertx
    lateinit var client: WebClient
    val baseUrl = "http://localhost:8080"

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

    describe("Health Check Endpoints") {

        describe("GET /health") {

            it("should return health status") {
                val response = client.get(8080, "localhost", "/health")
                    .send()
                    .coAwait()

                response.statusCode() shouldBe 200
                val body = response.bodyAsJsonObject()
                body.getString("status") shouldBe "UP"
                body.containsKey("checks") shouldBe true
            }

            it("should include database and redis checks") {
                val response = client.get(8080, "localhost", "/health")
                    .send()
                    .coAwait()

                val body = response.bodyAsJsonObject()
                val checks = body.getJsonArray("checks")

                checks.size() shouldBe 2

                val checkIds = checks.map { (it as JsonObject).getString("id") }
                (checkIds.contains("database")) shouldBe true
                (checkIds.contains("redis")) shouldBe true
            }
        }

        describe("GET /ready") {

            it("should return readiness status") {
                val response = client.get(8080, "localhost", "/ready")
                    .send()
                    .coAwait()

                response.statusCode() shouldBe 200
                val body = response.bodyAsJsonObject()
                body.getString("status") shouldBe "UP"
            }
        }

        describe("GET /live") {

            it("should return liveness status") {
                val response = client.get(8080, "localhost", "/live")
                    .send()
                    .coAwait()

                response.statusCode() shouldBe 200
                val body = response.bodyAsJsonObject()
                body.getString("status") shouldBe "UP"
            }
        }
    }
})
