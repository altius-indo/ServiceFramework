# Authorization System

This package implements a comprehensive authorization system based on the authorization requirements specification.

## Features

### Access Control Models

1. **Role-Based Access Control (RBAC)**
   - Hierarchical role structures with inheritance
   - Role assignment at user and group levels
   - Dynamic role evaluation

2. **Attribute-Based Access Control (ABAC)**
   - Policy-based access decisions using attributes
   - User, resource, and environmental attribute evaluation
   - Centralized policy decision point (PDP)

3. **Relationship-Based Access Control (ReBAC)**
   - Graph-based access control decisions
   - Ownership and delegation patterns
   - Transitive relationships support

### Permission Management

- Resource-level, field-level, and operation-level permissions
- Time-based permissions with expiration
- Conditional permissions based on runtime context
- Permission inheritance and delegation
- Conflict resolution

### Policy Engine

- Declarative policy definitions
- Version-controlled policies
- Policy evaluation caching
- Real-time policy updates
- Performance target: <10ms at p95

### Resource Authorization

- Resource ownership management
- Resource sharing (public, private, shared)
- Link-based sharing with optional passwords
- Hierarchical resource permissions
- Bulk permission management

### Multi-Tenancy

- Complete data isolation between tenants
- Separate encryption keys per tenant
- Resource quotas and limits
- Tenant-specific configuration
- Cross-tenant collaboration support

### Dynamic Authorization

- Context-aware authorization (time, location, device, network)
- Just-in-time access with approval workflows
- Usage-based authorization limits
- Risk-based adaptive authorization

### Authorization Audit

- Comprehensive access logging
- Structured logs for analysis
- Real-time streaming to SIEM systems
- Compliance reporting
- Forensics support

## Architecture

### Components

1. **Engines** (`engines/`)
   - `RbacEngine`: Role-based authorization
   - `AbacEngine`: Attribute-based authorization
   - `RebacEngine`: Relationship-based authorization

2. **Services** (`services/`)
   - `AuthorizationService`: Main orchestration service (PDP)
   - `PolicyService`: Policy management
   - `PermissionService`: Permission management
   - `AuditService`: Audit logging
   - `DynamicAuthorizationService`: Dynamic authorization features

3. **Repositories** (`repositories/`)
   - `RoleRepository`: Role storage
   - `PermissionRepository`: Permission storage
   - `PolicyRepository`: Policy storage
   - `RelationshipRepository`: Relationship storage
   - `TenantRepository`: Tenant storage
   - `ResourceRepository`: Resource storage

4. **Handlers** (`handlers/`)
   - `AuthorizationHandler`: HTTP request authorization (PEP)

5. **Models** (`models/`)
   - Core data models for roles, permissions, policies, resources, etc.

## Usage

### Deploying the Authorization Verticle

```kotlin
val vertx = Vertx.vertx()
val deploymentOptions = DeploymentOptions().setConfig(config)
vertx.deployVerticle(AuthorizationVerticle::class.java.name, deploymentOptions)
```

### Using Authorization Handler

```kotlin
val router = Router.router(vertx)

// Protect a route with authorization
router.get("/api/documents/:id")
    .handler(AuthorizationHandler(
        authorizationService,
        auditService,
        dynamicAuthzService,
        "read",
        "document"
    ))
    .handler { context ->
        // Your handler logic
    }
```

### Programmatic Authorization

```kotlin
val context = AuthorizationContext(
    subjectId = "user123",
    resourceId = "doc456",
    resourceType = "document",
    action = "read",
    tenantId = "tenant1"
)

val decision = authorizationService.authorize(context)
if (decision.allowed) {
    // Proceed with operation
}
```

### Creating Policies

```kotlin
val policy = Policy(
    id = UUID.randomUUID().toString(),
    name = "Allow Department Access",
    effect = PolicyEffect.ALLOW,
    actions = listOf("read", "write"),
    resources = listOf("document:*"),
    conditions = mapOf(
        "department" to PolicyCondition(
            attribute = "user.department",
            operator = ConditionOperator.EQUALS,
            value = "Engineering"
        )
    )
)

policyService.createPolicy(policy)
```

### Creating Roles

```kotlin
val role = Role(
    id = UUID.randomUUID().toString(),
    name = "Editor",
    permissions = listOf("document:read", "document:write"),
    parentRoleId = "viewer-role-id" // Inherit from viewer role
)

roleRepository.save(role)
```

## Configuration

Add to `application.json`:

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
    "port": 6379
  }
}
```

## Performance

- Authorization check latency: <10ms at p95
- Supports 1M+ authorization checks per second
- Caching with 99% hit rate target
- Bulk authorization checks for efficiency

## Compliance

The system supports:
- SOC 2 Type II requirements
- GDPR data access control compliance
- HIPAA authorization requirements
- PCI-DSS access control standards

## Integration Points

- Identity Provider integration (roles and groups sync)
- Standard authorization APIs (REST, gRPC)
- Data store integration (row-level security)
- Event bus integration for async operations

