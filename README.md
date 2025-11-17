# Enterprise Service Framework

A comprehensive, scalable, and secure enterprise service framework built with Vert.x and Kotlin.

## Overview

This repository contains a robust framework for building high-performance, enterprise-grade services. It is designed with a documentation-first philosophy and includes a comprehensive suite of tools and specifications to guide development, ensure consistency, and maintain high standards of quality.

**Key Features:**

*   **Authentication**: Multi-provider support (JWT, OAuth2, SAML)
*   **Authorization**: Comprehensive RBAC, ABAC, and ReBAC implementation with policy engine, permission management, multi-tenancy, and dynamic authorization
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

### Prerequisites

*   **JDK 17+** - For running the Kotlin/Java application
*   **Gradle 8.4+** - Build automation (wrapper included)
*   **Docker & Docker Compose** - For running dependencies (Redis, DynamoDB)

### Setup and Run

#### 1. Start Dependencies

The application requires Redis and DynamoDB. Start them using Docker Compose:

```bash
cd enterprise-service-framework
docker-compose -f docker/docker-compose.yml up -d
```

This will start:
- **Redis** on port `6379` (session/cache storage)
- **DynamoDB Local** on port `8000` (database)

Verify services are running:
```bash
docker ps
```

#### 2. Build and Run the Application

```bash
cd enterprise-service-framework
./gradlew run
```

The application will start on `http://localhost:8080`

#### 3. Verify the Service

Test the health endpoint:
```bash
curl http://localhost:8080/health
```

Expected response:
```json
{
  "status": "UP",
  "checks": [
    {"id": "database", "status": "UP"},
    {"id": "redis", "status": "UP"}
  ],
  "outcome": "UP"
}
```

### Available Endpoints

Once running, the following endpoints are available:

**Health Checks:**
- `GET /health` - Overall health status
- `GET /ready` - Readiness probe
- `GET /live` - Liveness probe

**Authentication:**
- `POST /auth/login` - User login
- `POST /auth/refresh` - Refresh access token
- `POST /auth/logout` - Logout (protected)
- `GET /auth/sessions` - List active sessions (protected)
- `DELETE /auth/sessions/:sessionId` - Terminate session (protected)
- `POST /auth/introspect` - Token introspection

**Authorization:**
- All API endpoints support authorization via `AuthorizationHandler`
- Event bus: `authz.authorize` - Programmatic authorization requests
- Event bus: `authz.audit` - Authorization audit logging

**API:**
- `/api/v1/*` - Protected API endpoints with authorization

### Stopping the Services

Stop the application with `Ctrl+C`, then stop Docker services:

```bash
cd enterprise-service-framework
docker-compose -f docker/docker-compose.yml down
```

### Docker Compose Only

To run everything (app + dependencies) in Docker:

```bash
cd enterprise-service-framework
# Uncomment the 'app' service in docker/docker-compose.yml first
docker-compose -f docker/docker-compose.yml up
```

### Kubernetes Deployment

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

### Building

Build the application without running it:

```bash
cd enterprise-service-framework
./gradlew build
```

The build creates:
- Standard JAR: `build/libs/enterprise-service-framework-1.0.0-SNAPSHOT.jar`
- Fat JAR (with dependencies): `build/libs/enterprise-service-framework-1.0.0-SNAPSHOT-all.jar`

### Testing

Run the test suite:

```bash
cd enterprise-service-framework
./gradlew test
```

### Running in Development Mode

For development with auto-reload (requires dependencies running):

```bash
cd enterprise-service-framework
./gradlew run --continuous
```

### Gradle Tasks

View all available Gradle tasks:

```bash
cd enterprise-service-framework
./gradlew tasks
```

Common tasks:
- `./gradlew clean` - Clean build artifacts
- `./gradlew build` - Build the project
- `./gradlew test` - Run tests
- `./gradlew run` - Run the application
- `./gradlew shadowJar` - Create fat JAR

## Configuration

The application's configuration is loaded from `enterprise-service-framework/src/main/resources/application.json`.

**Key configuration sections:**

*   Server settings
*   Database connections (DynamoDB, Redis)
*   Authentication (JWT secrets)
*   Authorization (policy engine, caching)
*   Logging levels

### Authorization Configuration

Add authorization settings to `application.json`:

```json
{
  "authorization": {
    "cache": {
      "enabled": true,
      "ttl": 60
    }
  },
  "redis": {
    "host": "localhost",
    "port": 6379,
    "password": null
  }
}
```

## Authorization System

The framework includes a comprehensive authorization system implementing RBAC, ABAC, and ReBAC models.

### Features

**Access Control Models:**
- **RBAC**: Hierarchical roles with inheritance, role assignment at user/group levels
- **ABAC**: Policy-based decisions using user, resource, and environmental attributes
- **ReBAC**: Graph-based access control with ownership, delegation, and transitive relationships

**Permission Management:**
- Resource-level, field-level, and operation-level permissions
- Time-based permissions with expiration
- Conditional permissions based on runtime context
- Permission inheritance and delegation with audit trails

**Policy Engine:**
- Declarative policy definitions with version control
- Centralized Policy Decision Point (PDP) and distributed Policy Enforcement Points (PEPs)
- Policy evaluation caching with configurable TTL
- Real-time policy updates without service restart
- Performance: <10ms policy evaluation at p95

**Resource Authorization:**
- Resource ownership management with transfer capabilities
- Resource sharing (public, private, shared) with link-based sharing
- Hierarchical resource permissions with inheritance
- Bulk permission management

**Multi-Tenancy:**
- Complete data isolation between tenants
- Separate encryption keys per tenant
- Resource quotas and limits per tenant
- Tenant-specific configuration and policies
- Cross-tenant collaboration with explicit opt-in

**Dynamic Authorization:**
- Context-aware authorization (time, location, device, network-based)
- Just-in-time (JIT) access with approval workflows
- Usage-based authorization limits and quotas
- Risk-based adaptive authorization

**Authorization Audit:**
- Comprehensive access logging (allow/deny decisions)
- Structured logs with full context for analysis
- Real-time streaming to SIEM systems
- Compliance reporting (SOX, HIPAA, GDPR, PCI-DSS)
- Forensics support with immutable audit trails

### Usage

**Deploying Authorization:**

```kotlin
val vertx = Vertx.vertx()
val deploymentOptions = DeploymentOptions().setConfig(config)
vertx.deployVerticle(AuthorizationVerticle::class.java.name, deploymentOptions)
```

**Protecting Routes:**

```kotlin
import com.enterprise.framework.authz.handlers.AuthorizationHandler
import com.enterprise.framework.authz.utils.AuthorizationUtils

val router = Router.router(vertx)

// Get services from verticle
val authzService = AuthorizationUtils.getAuthorizationService(vertx)
val auditService = AuthorizationUtils.getAuditService(vertx)
val dynamicAuthzService = AuthorizationUtils.getDynamicAuthzService(vertx)

// Protect a route
router.get("/api/documents/:id")
    .handler(AuthorizationHandler(
        authzService!!,
        auditService!!,
        dynamicAuthzService!!,
        "read",
        "document"
    ))
    .handler { context ->
        // Your handler logic - authorization already checked
    }
```

**Programmatic Authorization:**

```kotlin
import com.enterprise.framework.authz.models.AuthorizationContext
import com.enterprise.framework.authz.models.SubjectType

val context = AuthorizationContext(
    subjectId = "user123",
    subjectType = SubjectType.USER,
    resourceId = "doc456",
    resourceType = "document",
    action = "read",
    tenantId = "tenant1",
    userAttributes = mapOf("department" to "Engineering"),
    environmentalAttributes = mapOf("ipAddress" to "192.168.1.1")
)

val decision = authorizationService.authorize(context)
if (decision.allowed) {
    // Proceed with operation
} else {
    // Handle denial
    logger.warn { "Access denied: ${decision.reason}" }
}
```

**Creating Policies:**

```kotlin
import com.enterprise.framework.authz.models.*
import java.util.UUID

val policy = Policy(
    id = UUID.randomUUID().toString(),
    name = "Allow Engineering Department Access",
    effect = PolicyEffect.ALLOW,
    actions = listOf("read", "write"),
    resources = listOf("document:*"),
    conditions = mapOf(
        "department" to PolicyCondition(
            attribute = "user.department",
            operator = ConditionOperator.EQUALS,
            value = "Engineering"
        )
    ),
    priority = 100,
    tenantId = "tenant1"
)

policyService.createPolicy(policy)
```

**Creating Roles:**

```kotlin
import com.enterprise.framework.authz.models.Role
import java.util.UUID

val role = Role(
    id = UUID.randomUUID().toString(),
    name = "Document Editor",
    permissions = listOf("document:read", "document:write"),
    parentRoleId = "viewer-role-id", // Inherit from viewer role
    tenantId = "tenant1"
)

roleRepository.save(role)

// Assign role to user
val assignment = RoleAssignment(
    id = UUID.randomUUID().toString(),
    roleId = role.id,
    subjectId = "user123",
    subjectType = SubjectType.USER,
    assignedBy = "admin",
    tenantId = "tenant1"
)

roleRepository.assignRole(assignment)
```

For detailed authorization documentation, see `enterprise-service-framework/src/main/kotlin/com/enterprise/framework/authz/README.md`.

## Documentation

*   **Specifications**: See the `specs/` directory for detailed requirements and design documents
*   **Index**: `specs/INDEX.md` - Complete documentation index
*   **Quick Reference**: `specs/QUICK-REFERENCE.md` - Fast access to key information
*   **Authorization**: `enterprise-service-framework/src/main/kotlin/com/enterprise/framework/authz/README.md` - Authorization system documentation

## Troubleshooting

### Port Already in Use

If you see "Address already in use" error:

```bash
# Find process using port 8080
lsof -i :8080

# Kill the process (replace PID with actual process ID)
kill <PID>
```

### Docker Services Not Starting

Check Docker services status:

```bash
docker-compose -f enterprise-service-framework/docker/docker-compose.yml ps
```

View logs:

```bash
docker-compose -f enterprise-service-framework/docker/docker-compose.yml logs
```

Restart services:

```bash
docker-compose -f enterprise-service-framework/docker/docker-compose.yml restart
```

### Connection Refused to Redis/DynamoDB

Ensure Docker services are running:

```bash
docker ps | grep enterprise
```

You should see:
- `enterprise-redis`
- `enterprise-dynamodb`

If not running, start them:

```bash
cd enterprise-service-framework
docker-compose -f docker/docker-compose.yml up -d
```

### Build Failures

Clean and rebuild:

```bash
cd enterprise-service-framework
./gradlew clean build
```

## Contributing

1.  Fork the repository
2.  Create a feature branch
3.  Make your changes
4.  Submit a pull request

## License

Copyright © 2025 Enterprise Service Framework
