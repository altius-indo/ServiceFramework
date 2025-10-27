package com.enterprise.framework.service

import io.vertx.circuitbreaker.CircuitBreaker
import io.vertx.circuitbreaker.CircuitBreakerOptions
import io.vertx.core.Vertx
import io.vertx.kotlin.coroutines.await
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Circuit breaker service for fault tolerance
 */
class CircuitBreakerService(
    private val vertx: Vertx,
    private val name: String = "default-circuit-breaker",
    private val maxFailures: Int = 5,
    private val timeout: Long = 10000,
    private val resetTimeout: Long = 30000
) {

    private val circuitBreaker: CircuitBreaker

    init {
        logger.info { "Initializing circuit breaker: $name" }

        val options = CircuitBreakerOptions()
            .setMaxFailures(maxFailures)
            .setTimeout(timeout)
            .setResetTimeout(resetTimeout)
            .setFallbackOnFailure(true)

        circuitBreaker = CircuitBreaker.create(name, vertx, options)
            .openHandler {
                logger.warn { "Circuit breaker [$name] opened" }
            }
            .halfOpenHandler {
                logger.info { "Circuit breaker [$name] half-open" }
            }
            .closeHandler {
                logger.info { "Circuit breaker [$name] closed" }
            }
    }

    suspend fun <T> execute(operation: suspend () -> T, fallback: suspend () -> T): T {
        return try {
            circuitBreaker.execute<T> { promise ->
                vertx.executeBlocking<T> { blockingPromise ->
                    try {
                        val result = kotlinx.coroutines.runBlocking { operation() }
                        blockingPromise.complete(result)
                    } catch (e: Exception) {
                        logger.error(e) { "Circuit breaker operation failed" }
                        blockingPromise.fail(e)
                    }
                }.onComplete { result ->
                    if (result.succeeded()) {
                        promise.complete(result.result())
                    } else {
                        promise.fail(result.cause())
                    }
                }
            }.await()
        } catch (e: Exception) {
            logger.warn(e) { "Circuit breaker caught exception, executing fallback" }
            fallback()
        }
    }

    fun state(): String {
        return circuitBreaker.state().name
    }

    fun stats(): Map<String, Any> {
        return mapOf(
            "name" to name,
            "state" to state(),
            "failures" to circuitBreaker.failureCount()
        )
    }

    fun reset() {
        logger.info { "Resetting circuit breaker: $name" }
        circuitBreaker.reset()
    }
}
