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
 * Metrics service for application monitoring
 */
class MetricsService(private val vertx: Vertx) {

    private val registry: MeterRegistry

    init {
        logger.info { "Initializing metrics service" }

        registry = BackendRegistries.getDefaultNow() ?: SimpleMeterRegistry()
    }

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

    fun <T> measureTime(name: String, tags: Map<String, String> = emptyMap(), block: () -> T): T {
        val startTime = System.currentTimeMillis()
        return try {
            block()
        } finally {
            val duration = System.currentTimeMillis() - startTime
            recordTime(name, duration, tags)
        }
    }

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

    fun getRegistry(): MeterRegistry {
        return registry
    }

    companion object {
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
