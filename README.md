# Enterprise Service Framework

A comprehensive, scalable, and secure enterprise service framework built with Vert.x and Kotlin.

## Overview

This repository contains a robust framework for building high-performance, enterprise-grade services. It is designed with a documentation-first philosophy and includes a comprehensive suite of tools and specifications to guide development, ensure consistency, and maintain high standards of quality.

**Key Features:**

*   **Authentication**: Multi-provider support (JWT, OAuth2, SAML)
*   **Authorization**: RBAC, ABAC, ReBAC
*   **Rate Limiting**: Multiple strategies (Token Bucket, Leaky Bucket, etc.)
*   **Distributed Tracing**: OpenTelemetry integration
*   **Monitoring**: Metrics, health checks, and alerts
*   **Event-Driven**: Pub/sub with Kafka
*   **High Availability**: Circuit breakers, bulkheads, and cellularization

## Documentation-First Philosophy

This project follows a documentation-first approach. All technical specifications are generated from a Python script and are intended to be the primary source of truth for the system's architecture and requirements.

### Generating Specifications

To generate or update the technical specification documents, run the following command from the root of the repository:

```bash
python3 generate_specs.py --all
```

The generated documents will be placed in the `specs` directory. For a detailed guide on the documentation and a recommended reading order, please see `specs/INDEX.md`.

## Quick Start

### Local Development

1.  **Navigate to the framework directory:**
    ```bash
    cd enterprise-service-framework
    ```

2.  **Build and run the application:**
    ```bash
    ./gradlew run
    ```

3.  **Access the API:**
    ```bash
    curl http://localhost:8080/health
    ```

### Docker Compose

To run the application using Docker Compose:

```bash
cd enterprise-service-framework
docker-compose -f docker/docker-compose.yml up
```

### Kubernetes

To deploy the application to a Kubernetes cluster:

```bash
cd enterprise-service-framework
kubectl apply -f k8s/
```

## Project Structure

```
.
├── enterprise-service-framework/ # The main application source code
│   ├── src/main/kotlin/          # Application source code
│   ├── src/main/resources/       # Configuration files
│   ├── src/test/kotlin/          # Test files
│   ├── docker/                   # Docker configurations
│   ├── k8s/                      # Kubernetes manifests
│   └── scripts/                  # Utility scripts
├── specs/                        # Technical specification documents
├── generate_specs.py             # Script for generating specifications
└── project_setup.sh              # Script for initial project setup
```

## Development

### Prerequisites

*   JDK 17+
*   Gradle 8.4+
*   Docker
*   `kubectl` (for Kubernetes deployment)

### Building

To build the application, run:

```bash
cd enterprise-service-framework
./gradlew build
```

### Testing

To run the test suite, run:

```bash
cd enterprise-service-framework
./gradlew test
```

### Running Locally

To run the application locally, use:

```bash
cd enterprise-service-framework
./gradlew run
```

## Configuration

The application's configuration is loaded from `enterprise-service-framework/src/main/resources/application.json`.

**Key configuration sections:**

*   Server settings
*   Database connections (DynamoDB, Redis)
*   Authentication (JWT secrets)
*   Logging levels

## Documentation

*   **Specifications**: See the `specs/` directory for detailed requirements and design documents
*   **Index**: `specs/INDEX.md` - Complete documentation index
*   **Quick Reference**: `specs/QUICK-REFERENCE.md` - Fast access to key information

## Contributing

1.  Fork the repository
2.  Create a feature branch
3.  Make your changes
4.  Submit a pull request

## License

Copyright © 2025 Enterprise Service Framework
