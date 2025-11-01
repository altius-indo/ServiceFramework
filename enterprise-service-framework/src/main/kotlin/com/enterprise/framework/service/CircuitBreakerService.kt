package com.enterprise.framework.service

import io.vertx.circuitbreaker.CircuitBreaker
import io.vertx.circuitbreaker.CircuitBreakerOptions
import io.vertx.core.Vertx
import io.vertx.kotlin.coroutines.await
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * A service that provides circuit breaker functionality to enhance fault tolerance.
 *
 * This service wraps the Vert.x [CircuitBreaker] to protect the system from failures
 * in remote services or resources. It monitors for failures and, after a certain
 * threshold, opens the circuit to prevent further calls to the failing service.
 *
 * @property vertx The Vert.x instance.
 * @property name The name of the circuit breaker.
 * @property maxFailures The maximum number of failures before the circuit breaker opens.
 * @property timeout The timeout for the operation, in milliseconds.
 * @property resetTimeout The time after which the circuit breaker will try to reset, in milliseconds.
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

    /**
     * Executes an operation protected by the circuit breaker.
     *
     * This method wraps the given operation in the circuit breaker's logic. If the
     * operation fails or the circuit is open, it will execute the provided
     * fallback function.
     *
     * @param T The return type of the operation.
     * @param operation The suspend function to execute.
     * @param fallback The suspend function to execute if the main operation fails.
     * @return The result of the operation or the fallback.
     */
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

    /**
     * Returns the current state of the circuit breaker.
     *
     * @return A string representation of the circuit breaker's state (e.g., "OPEN", "CLOSED").
     */
    fun state(): String {
        return circuitBreaker.state().name
    }

    /**
     * Returns a map of statistics for the circuit breaker.
     *
     * This method provides information about the circuit breaker's name, current
     * state, and the number of failures recorded.
     *
     * @return A map containing the circuit breaker's statistics.
     */
    fun stats(): Map<String, Any> {
        return mapOf(
            "name" to name,
            "state" to state(),
            "failures" to circuitBreaker.failureCount()
        )
    }

    /**
     * Resets the circuit breaker to its initial state.
     *
     * This method closes the circuit and resets the failure count, allowing
     * operations to be executed again.
     */
    fun reset() {
        logger.info { "Resetting circuit breaker: $name" }
        circuitBreaker.reset()
    }
}
