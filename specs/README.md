# Enterprise Service Framework

A comprehensive enterprise service framework built with Vert.x and Kotlin.

Project connected to claude code

## Features

- **Authentication**: Multi-provider support (JWT, OAuth2, SAML)
- **Authorization**: RBAC, ABAC, ReBAC
- **Rate Limiting**: Multiple strategies (Token Bucket, Leaky Bucket, etc.)
- **Distributed Tracing**: OpenTelemetry integration
- **Monitoring**: Metrics, health checks, alerts
- **Event-Driven**: Pub/sub with Kafka
- **High Availability**: Circuit breakers, bulkheads, cellularization

## Quick Start

### Local Development

1. Start local services:
```bash
./scripts/setup-local.sh
```

2. Build and run:
```bash
./gradlew run
```

3. Access the API:
```bash
curl http://localhost:8080/health
```

### Docker Compose

```bash
docker-compose -f docker/docker-compose.yml up
```

### Kubernetes

```bash
./scripts/deploy.sh
```

## Project Structure

```
enterprise-service-framework/
├── src/main/kotlin/          # Application source code
├── src/main/resources/       # Configuration files
├── src/test/kotlin/          # Test files
├── docker/                   # Docker configurations
├── k8s/                      # Kubernetes manifests
├── scripts/                  # Utility scripts
└── docs/                     # Documentation
```

## Development

### Prerequisites

- JDK 17+
- Gradle 8.4+
- Docker
- kubectl (for K8s deployment)

### Building

```bash
./gradlew build
```

### Testing

```bash
./gradlew test
```

### Running locally

```bash
./gradlew run
```

## Configuration

Configuration is loaded from `src/main/resources/application.conf`.

Key configuration sections:
- Server settings
- Database connections (DynamoDB, Redis)
- Authentication (JWT secrets)
- Logging levels

## API Documentation

See [OpenAPI specification](docs/api/openapi.yaml)

## Architecture

See [Architecture documentation](docs/architecture/diagrams/system-overview.md)

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Submit a pull request

## License

Copyright © 2025 Enterprise Service Framework
