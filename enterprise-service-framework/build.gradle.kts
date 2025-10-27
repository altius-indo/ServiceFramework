import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.9.20"
    application
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "com.enterprise"
version = "1.0.0-SNAPSHOT"

repositories {
    mavenCentral()
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    // Vert.x
    implementation("io.vertx:vertx-core:4.5.0")
    implementation("io.vertx:vertx-web:4.5.0")
    implementation("io.vertx:vertx-lang-kotlin:4.5.0")
    implementation("io.vertx:vertx-lang-kotlin-coroutines:4.5.0")
    implementation("io.vertx:vertx-config:4.5.0")
    implementation("io.vertx:vertx-auth-jwt:4.5.0")
    implementation("io.vertx:vertx-auth-oauth2:4.5.0")
    implementation("io.vertx:vertx-redis-client:4.5.0")
    implementation("io.vertx:vertx-circuit-breaker:4.5.0")
    implementation("io.vertx:vertx-health-check:4.5.0")
    implementation("io.vertx:vertx-micrometer-metrics:4.5.0")
    
    // Kotlin
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    
    // AWS SDK
    implementation("software.amazon.awssdk:dynamodb:2.20.0")
    implementation("software.amazon.awssdk:dynamodb-enhanced:2.20.0")
    
    // Logging
    implementation("io.github.microutils:kotlin-logging-jvm:3.0.5")
    implementation("ch.qos.logback:logback-classic:1.4.11")
    
    // OpenTelemetry
    implementation("io.opentelemetry:opentelemetry-api:1.32.0")
    implementation("io.opentelemetry:opentelemetry-sdk:1.32.0")
    
    // Jackson
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.3")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.15.3")
    
    // Testing
    testImplementation("io.vertx:vertx-junit5:4.5.0")
    testImplementation("io.kotest:kotest-runner-junit5:5.8.0")
    testImplementation("io.kotest:kotest-assertions-core:5.8.0")
    testImplementation("io.mockk:mockk:1.13.8")
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = listOf("-Xjsr305=strict")
        jvmTarget = "17"
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

application {
    mainClass.set("com.enterprise.framework.MainKt")
}
