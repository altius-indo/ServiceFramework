package com.enterprise.framework.service

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.vertx.core.Vertx
import io.vertx.micrometer.MicrometerMetricsOptions
import io.vertx.micrometer.VertxPrometheusOptions
import io.vertx.micrometer.backends.BackendRegistries
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * A service for collecting and managing application metrics.
 *
 * This service provides a simple interface for recording various types of metrics,
 * such as counters and timers, which are essential for monitoring the health and
 * performance of the application.
 *
 * @property vertx The Vert.x instance.
 */
class MetricsService(private val vertx: Vertx) {

    private val registry: MeterRegistry

    init {
        logger.info { "Initializing metrics service" }

        registry = BackendRegistries.getDefaultNow() ?: SimpleMeterRegistry()
    }

    /**
     * Increments a counter metric.
     *
     * @param name The name of the counter.
     * @param tags A map of tags to associate with the metric for dimensionality.
     */
    fun incrementCounter(name: String, tags: Map<String, String> = emptyMap()) {
        val counter = Counter.builder(name)
            .apply {
                tags.forEach { (key, value) ->
                    tag(key, value)
                }
            }
            .register(registry)

        counter.increment()
        logger.debug { "Incremented counter: $name" }
    }

    /**
     * Records a time-based metric.
     *
     * @param name The name of the timer.
     * @param durationMs The duration to record, in milliseconds.
     * @param tags A map of tags to associate with the metric.
     */
    fun recordTime(name: String, durationMs: Long, tags: Map<String, String> = emptyMap()) {
        val timer = Timer.builder(name)
            .apply {
                tags.forEach { (key, value) ->
                    tag(key, value)
                }
            }
            .register(registry)

        timer.record(java.time.Duration.ofMillis(durationMs))
        logger.debug { "Recorded timer: $name with duration $durationMs ms" }
    }

    /**
     * Measures the execution time of a block of code and records it as a timer metric.
     *
     * @param T The return type of the code block.
     * @param name The name of the timer.
     * @param tags A map of tags to associate with the metric.
     * @param block The block of code to execute and measure.
     * @return The result of the executed block.
     */
    fun <T> measureTime(name: String, tags: Map<String, String> = emptyMap(), block: () -> T): T {
        val startTime = System.currentTimeMillis()
        return try {
            block()
        } finally {
            val duration = System.currentTimeMillis() - startTime
            recordTime(name, duration, tags)
        }
    }

    /**
     * Measures the execution time of a suspendable block of code and records it as a timer metric.
     *
     * This version of `measureTime` is designed for use with coroutines.
     *
     * @param T The return type of the code block.
     * @param name The name of the timer.
     * @param tags A map of tags to associate with the metric.
     * @param block The suspendable block of code to execute and measure.
     * @return The result of the executed block.
     */
    suspend fun <T> measureTimeSuspend(
        name: String,
        tags: Map<String, String> = emptyMap(),
        block: suspend () -> T
    ): T {
        val startTime = System.currentTimeMillis()
        return try {
            block()
        } finally {
            val duration = System.currentTimeMillis() - startTime
            recordTime(name, duration, tags)
        }
    }

    /**
     * Returns the underlying [MeterRegistry] instance.
     *
     * This can be used to access more advanced features of the Micrometer library.
     *
     * @return The [MeterRegistry] used by this service.
     */
    fun getRegistry(): MeterRegistry {
        return registry
    }

    companion object {
        /**
         * Creates a new instance of [MetricsService] with Prometheus metrics enabled.
         *
         * This factory method configures the service to expose metrics in the Prometheus
         * format via an embedded HTTP server.
         *
         * @param vertx The Vert.x instance.
         * @return A new `MetricsService` instance configured for Prometheus.
         */
        fun createWithPrometheus(vertx: Vertx): MetricsService {
            val options = MicrometerMetricsOptions()
                .setPrometheusOptions(
                    VertxPrometheusOptions()
                        .setEnabled(true)
                        .setStartEmbeddedServer(true)
                        .setEmbeddedServerOptions(
                            io.vertx.core.http.HttpServerOptions().setPort(9090)
                        )
                )
                .setEnabled(true)

            return MetricsService(vertx)
        }
    }
}
