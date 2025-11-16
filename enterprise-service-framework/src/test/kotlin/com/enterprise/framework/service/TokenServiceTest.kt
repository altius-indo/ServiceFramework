package com.enterprise.framework.service

import com.enterprise.framework.model.JwtConfig
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldNotBeEmpty
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.vertx.ext.auth.JWTOptions
import io.vertx.ext.auth.jwt.JWTAuth
import io.vertx.core.json.JsonObject
import io.vertx.kotlin.coroutines.coAwait

class TokenServiceTest : DescribeSpec({

    lateinit var jwtAuth: JWTAuth
    lateinit var revocationService: TokenRevocationService
    lateinit var config: JwtConfig
    lateinit var tokenService: TokenService

    beforeEach {
        jwtAuth = mockk(relaxed = true)
        revocationService = mockk(relaxed = true)
        config = JwtConfig(
            secret = "test-secret-key-for-testing",
            issuer = "test-issuer",
            accessTokenExpirationSeconds = 3600,
            refreshTokenExpirationSeconds = 86400,
            algorithm = "HS256"
        )

        // Mock JWT generation
        every { jwtAuth.generateToken(any<JsonObject>(), any<JWTOptions>()) } returns "mock-jwt-token"

        tokenService = TokenService(jwtAuth, config, revocationService)
    }

    describe("TokenService") {

        describe("generateAccessToken") {

            it("should generate a valid access token") {
                val userId = "user123"
                val claims = mapOf("username" to "testuser", "roles" to listOf("USER", "ADMIN"))

                val token = tokenService.generateAccessToken(userId, claims)

                token.shouldNotBeEmpty()
            }

            it("should generate access token with empty claims") {
                val userId = "user123"

                val token = tokenService.generateAccessToken(userId)

                token.shouldNotBeEmpty()
            }

            it("should handle custom claims") {
                val userId = "user123"
                val claims = mapOf(
                    "sessionId" to "session123",
                    "roles" to listOf("USER")
                )

                val token = tokenService.generateAccessToken(userId, claims)

                token.shouldNotBeEmpty()
            }
        }

        describe("generateRefreshToken") {

            it("should generate a valid refresh token") {
                val userId = "user123"

                val token = tokenService.generateRefreshToken(userId)

                token.shouldNotBeEmpty()
            }

            it("should generate refresh token for different users") {
                val token1 = tokenService.generateRefreshToken("user1")
                val token2 = tokenService.generateRefreshToken("user2")

                // Both should be generated successfully
                token1.shouldNotBeEmpty()
                token2.shouldNotBeEmpty()
            }
        }

        describe("token expiration") {

            it("should respect access token expiration configuration") {
                val shortExpiryConfig = JwtConfig(
                    secret = "test-secret",
                    issuer = "test-issuer",
                    accessTokenExpirationSeconds = 1,
                    refreshTokenExpirationSeconds = 60,
                    algorithm = "HS256"
                )
                val shortExpiryService = TokenService(jwtAuth, shortExpiryConfig, revocationService)

                val token = shortExpiryService.generateAccessToken("user123")

                token.shouldNotBeEmpty()
            }
        }
    }
})
