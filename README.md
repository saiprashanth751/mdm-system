# Mobile Device Management (MDM) System

A production-grade backend system for managing mobile app deployments across a fleet of devices. Built for MoveInSync to handle version lifecycle management, controlled rollouts, and real-time device tracking.

## Tech Stack

| Layer | Technology |
|---|---|
| Framework | Spring Boot 3.5 (Java 17) |
| Database | PostgreSQL 16 |
| Cache | Redis 7 |
| Message Broker | Apache Kafka 3.7 (KRaft mode — no ZooKeeper) |
| Auth | JWT (HS384) + BCrypt |
| Migrations | Flyway |
| Docs | SpringDoc OpenAPI (Swagger) |
| Monitoring | Prometheus + Grafana + Spring Actuator |
| Containerization | Docker Compose |

## Quick Start

### Prerequisites
- Java 17+
- Docker & Docker Compose
- Maven (wrapper included)

### Run

```bash
# 1. Start all infrastructure (PostgreSQL + Redis + Kafka)
docker-compose up -d postgres redis kafka

# 2. Start the application
./mvnw spring-boot:run

# 3. Open Swagger UI
# http://localhost:8081/swagger-ui.html

# Or run everything together:
docker-compose up --build
```

### Default Admin Credentials

| Username | Password | Role |
|---|---|---|
| `super_admin` | `admin123` | SUPER_ADMIN — Full access, approve/reject schedules |
| `release_engineer` | `admin123` | RELEASE_ENGINEER — Publish versions, create schedules |
| `ops_viewer` | `admin123` | OPS_VIEWER — Read-only dashboard access |

> Accounts are seeded automatically on first startup via `DataInitializer` (only when the admins table is empty).

---

## Architecture

```
┌────────────────────────────────────────────────────────────────────┐
│                         Client / Device                            │
│            (Heartbeat, Register, Report Update Progress)           │
└──────────────────────────┬─────────────────────────────────────────┘
                           │ HTTP/REST
┌──────────────────────────▼─────────────────────────────────────────┐
│                      Spring Security Filter                        │
│              JWT Authentication + Role-Based Access                │
│                                                                    │
│  Public: /auth/login, /devices/register, /devices/heartbeat        │
│  SUPER_ADMIN: approve/reject schedules                             │
│  RELEASE_ENGINEER: publish versions, create schedules              │
│  Authenticated: dashboard, audit, device listing                   │
└──────────────────────────┬─────────────────────────────────────────┘
                           │
┌──────────────────────────▼─────────────────────────────────────────┐
│                       Controller Layer (5)                         │
│     AuthController · DeviceController · AppVersionController       │
│            UpdateController · DashboardController                  │
└──────────────────────────┬─────────────────────────────────────────┘
                           │
┌──────────────────────────▼─────────────────────────────────────────┐
│                        Service Layer (7)                           │
│  DeviceService · AppVersionService · VersionCompatibilityService   │
│  UpdateScheduleService · DeviceUpdateService · DashboardService    │
│              AuditService · HeartbeatBufferService                  │
│                                                                    │
│  Key algorithms:                                                   │
│  • BFS shortest upgrade path through version compatibility graph   │
│  • State machine enforcement for update lifecycle                  │
│  • Percentage-based batch selection for phased rollouts            │
└───────────┬────────────────────┬───────────────────┬───────────────┘
            │                    │                   │
┌───────────▼───────────┐ ┌──────▼─────────────┐ ┌──▼─────────────────┐
│   PostgreSQL (JPA)    │ │  Redis             │ │   Apache Kafka     │
│                       │ │                    │ │                    │
│  7 tables:            │ │  Caching:          │ │  schedule.approved  │
│  admins               │ │  dashboard  5min   │ │  (3 partitions)    │
│  app_versions         │ │  versions   30min  │ │                    │
│  version_compatibility│ │  compatibility 15m │ │  Consumer:         │
│  devices              │ │                    │ │  ScheduleApproval  │
│  update_schedules     │ │  Heartbeat Buffer: │ │  Consumer (batched)│
│  device_updates       │ │  heartbeat:{imei}  │ │                    │
│  audit_logs           │ │  (write buffer)    │ │  KRaft mode        │
└───────────────────────┘ └────────────────────┘ └────────────────────┘
```

### Project Structure

```
src/main/java/com/moveinsync/mdm/
├── config/          # CacheConfig, KafkaConfig, DataInitializer, OpenApiConfig
├── controller/      # 5 REST controllers
├── dto/
│   ├── request/     # 8 request DTOs with Jakarta validation
│   └── response/    # 10 response DTOs
├── entity/          # 7 JPA entities
├── enums/           # 7 enums (incl. UpdateState state machine)
├── event/           # ScheduleApprovedEvent (Kafka payload)
├── exception/       # 7 custom exceptions + GlobalExceptionHandler
├── job/             # HeartbeatFlushJob (Redis → PostgreSQL batch flush)
├── kafka/           # ScheduleApprovalConsumer (async event processing)
├── repository/      # 7 JPA repositories with custom JPQL
├── security/        # JWT service, filter, UserDetailsService, SecurityConfig
└── service/         # 7 service classes + HeartbeatBufferService
```

---

## API Endpoints

### Authentication
| Method | Endpoint | Access | Description |
|---|---|---|---|
| POST | `/api/v1/auth/login` | Public | Login, returns JWT |

### Devices
| Method | Endpoint | Access | Description |
|---|---|---|---|
| POST | `/api/v1/devices/register` | Public | Register device by IMEI |
| POST | `/api/v1/devices/heartbeat` | Public | Device heartbeat + pending update check |
| GET | `/api/v1/devices` | Authenticated | Paginated list with filters |
| GET | `/api/v1/devices/{id}` | Authenticated | Device details |

### Versions
| Method | Endpoint | Access | Description |
|---|---|---|---|
| POST | `/api/v1/versions` | RELEASE_ENGINEER+ | Publish new version (immutable) |
| GET | `/api/v1/versions` | Authenticated | List all versions |
| POST | `/api/v1/versions/compatibility` | RELEASE_ENGINEER+ | Create upgrade rule |
| GET | `/api/v1/versions/compatibility/check` | Authenticated | BFS upgrade path check |

### Updates
| Method | Endpoint | Access | Description |
|---|---|---|---|
| POST | `/api/v1/updates/schedule` | RELEASE_ENGINEER+ | Schedule an update |
| GET | `/api/v1/updates/schedule/{id}` | Authenticated | Schedule details |
| PUT | `/api/v1/updates/schedule/{id}/approve` | SUPER_ADMIN | Approve schedule |
| PUT | `/api/v1/updates/schedule/{id}/reject` | SUPER_ADMIN | Reject with reason |
| PUT | `/api/v1/updates/{id}/status` | Public (device) | Report update progress |

### Dashboard & Audit
| Method | Endpoint | Access | Description |
|---|---|---|---|
| GET | `/api/v1/dashboard/summary` | Authenticated | Device counts, version distribution, rollout progress |
| GET | `/api/v1/audit/devices/{id}` | Authenticated | Device event timeline |
| GET | `/api/v1/audit/schedules/{id}` | Authenticated | Schedule event timeline |

---

## Design Decisions & Trade-offs

### 1. BFS for Upgrade Path Finding

**Decision:** Use Breadth-First Search on the version compatibility graph.

**Why:** The compatibility rules form a directed graph where versions are nodes and allowed upgrades are edges. BFS finds the shortest (fewest hops) upgrade path, which minimizes the number of intermediate installs a device must go through.

**Complexity:** `O(V + E)` where V = number of versions, E = number of compatibility rules. For a typical MDM system with ~50 versions and ~100 rules, this executes in microseconds.

**Trade-off:** We load the entire compatibility graph into memory for each path check. For very large graphs (>10K versions), we'd want to cache the adjacency list. Currently, the Redis cache on the compatibility endpoint (15-minute TTL) mitigates repeated lookups.

**Alternative considered:** Dijkstra's algorithm — unnecessary overhead since all edge weights are equal (each upgrade is one step). Floyd-Warshall for all-pairs shortest paths — precomputes every possible path but costs `O(V³)` space and time, wasteful when queries are infrequent.

### 2. State Machine via Enum with Static Transition Map

**Decision:** Encode the update lifecycle states and allowed transitions directly in the `UpdateState` enum.

```
SCHEDULED → NOTIFIED → DOWNLOAD_STARTED → DOWNLOAD_COMPLETED
    → INSTALLATION_STARTED → INSTALLATION_COMPLETED
Any active state → FAILED
FAILED → SCHEDULED (retry)
```

**Why:** Compile-time safety. The transition map is defined once and cannot be bypassed. No configuration file to get out of sync, no database table to maintain.

**Trade-off:** Adding a new state requires a code change + redeployment. In practice, update lifecycle states change very rarely (maybe once a year), so this is acceptable. If states needed to be configurable at runtime, we'd use a database-backed state machine (e.g., Spring Statemachine), but that adds significant complexity.

### 3. UUID Primary Keys vs Sequential IDs

**Decision:** Use `UUID` (v4) for all entity primary keys.

**Why:**
- No ID enumeration attacks (can't guess `device/2` to access someone else's device)
- Safe for future multi-region or distributed deployments (no sequence coordination needed)
- Generated in the application layer — no DB round-trip for ID generation

**Trade-off:** UUIDs are 128 bits vs 64 bits for `BIGINT`. This means ~2x index size and slightly slower B-tree lookups. For our expected scale (~50K devices), this is negligible. At 10M+ devices, we'd consider `BIGINT` with `TSID` (time-sorted IDs) for better index locality.

### 4. Append-Only Audit Log

**Decision:** `AuditLog` entity has no update or delete operations. Metadata is stored as JSONB for schema flexibility.

**Why:** Compliance and accountability. An append-only log is tamper-proof by design. JSONB allows different event types to carry different metadata without schema changes.

**Trade-off:** The audit table grows indefinitely. For production, we'd add:
- Table partitioning by `created_at` (monthly partitions)
- Archival to cold storage after 90 days
- Materialized views for common aggregation queries

**Space complexity:** Each audit row is ~500 bytes. At 1M events/month = ~500MB/month = ~6GB/year. Well within PostgreSQL capacity.

### 5. Redis Caching Strategy

**Decision:** Three separate caches with different TTLs:

| Cache | TTL | Rationale |
|---|---|---|
| `dashboard:summary` | 5 minutes | Aggregation queries are expensive; 5-min staleness is acceptable for dashboard |
| `versions:all` | 30 minutes | Versions are immutable once published — cache aggressively |
| `compatibility:paths` | 15 minutes | Rules change infrequently but more often than versions |

**Trade-off:** Cache invalidation on version publish and compatibility rule creation would give fresher data, but adds complexity. Given that an admin publishes maybe 1-2 versions per week, the TTL approach is simpler and sufficient.

### 6. Phased Rollout via Percentage-Based Batch Selection

**Decision:** For phased rollouts, select a random subset of target devices equal to `rolloutPercentage` of the total matching devices.

**Why:** Prevents "thundering herd" — if 10K devices all try to download an update simultaneously, the backend and CDN would be overwhelmed. A 10% phased rollout sends updates to ~1K devices first.

**Trade-off:** The current implementation selects devices randomly on schedule creation. A more sophisticated system would:
- Allow multiple phases (10% → 25% → 50% → 100%)
- Prioritize devices by region or priority tier
- Implement automatic rollback if failure rate exceeds threshold

### 7. Downgrade Prevention — Triple Layer

**Decision:** Block downgrades at three independent levels:

1. **Compatibility rule level:** Cannot create a rule where `fromVersionCode > toVersionCode`
2. **Schedule level:** `UpdateScheduleService` rejects schedules where target < source
3. **Device level:** `DeviceUpdateService` checks the device's current version before installation

**Why:** Defense in depth. Even if one layer has a bug, the other two catch it. This is critical for MDM — a forced downgrade could break device functionality.

### 8. HikariCP Connection Pool Tuning

| Parameter | Value | Rationale |
|---|---|---|
| `maximum-pool-size` | 20 | Handles ~20 concurrent DB operations. Default (10) is too low for scheduled rollouts that create many `DeviceUpdate` records. |
| `minimum-idle` | 5 | Keeps 5 warm connections to avoid cold-start latency on the first request after idle. |
| `idle-timeout` | 300s | Reclaims idle connections after 5 minutes — balances resource usage vs. connection reuse. |
| `connection-timeout` | 30s | Fail fast if the pool is exhausted — prevents request queue buildup. |

---

## Event-Driven Architecture

### Kafka — Schedule Approval Processing

When an admin approves a schedule, the HTTP response returns immediately. The actual device notification happens asynchronously via Kafka:

```
Admin approves ──► KafkaTemplate.send("schedule.approved") ──► HTTP 200 (instant)
                                    │
                    ┌───────────────▼───────────────┐
                    │  ScheduleApprovalConsumer      │
                    │  • Batches of 500 records      │
                    │  • SCHEDULED → NOTIFIED        │
                    │  • Prometheus metrics           │
                    │  • At-least-once delivery       │
                    └───────────────────────────────┘
```

**Why Kafka (not Spring Events):** Survives JVM crashes, scales horizontally across instances, messages are persistent and replayed on restart.

### Redis — Heartbeat Write Buffer

At 100K devices heartbeating every 5 minutes = 333 writes/sec to PostgreSQL. The Redis buffer reduces this by ~100,000x:

```
Device heartbeat ──► Redis HSET heartbeat:{imei} ──► HTTP 200 (sub-ms)
                                    │
                    ┌───────────────▼───────────────┐
                    │  HeartbeatFlushJob              │
                    │  • @Scheduled every 30 seconds  │
                    │  • SCAN + batch UPDATE to PG    │
                    │  • Delete processed Redis keys  │
                    └───────────────────────────────┘
```

**Why Redis (not Kafka):** Heartbeats are idempotent — losing one in a crash is harmless (device sends another in 5 minutes). Kafka's persistence guarantees add unnecessary overhead here.

### Fault Tolerance

- **Redis outage:** `CacheErrorHandler` catches Redis failures and falls through to PostgreSQL. Application degrades gracefully — slower but operational.
- **Kafka outage:** At-least-once delivery ensures messages are replayed on recovery.
- **Dashboard cache:** `@CacheEvict("dashboard")` on state transitions ensures real-time accuracy.

---

## Monitoring & Observability

### Prometheus Metrics

| Metric | Type | Description |
|---|---|---|
| `mdm.device.registered.total` | Counter | Total device registrations |
| `mdm.heartbeat.received.total` | Counter | Total heartbeats received |
| `mdm.heartbeat.flush.total` | Counter | Devices flushed from Redis to PostgreSQL |
| `mdm.heartbeat.flush.errors` | Counter | Flush errors |
| `mdm.kafka.schedule.processed.total` | Counter | Kafka schedule approval events processed |
| `mdm.kafka.devices.notified.total` | Counter | Devices notified via Kafka async processing |
| `mdm.kafka.schedule.failed.total` | Counter | Failed Kafka schedule processing |
| `mdm.update.state_transition.total` | Counter | Update state transitions |

### Endpoints

- **Prometheus:** `GET /actuator/prometheus` — All metrics in Prometheus format
- **Health:** `GET /actuator/health` — Application + dependency health
- **Caches:** `GET /actuator/caches` — Redis cache statistics

### Grafana Dashboard

Importable dashboard at `monitoring/grafana-dashboard.json` with panels for:
- Device registrations and heartbeat rate
- Redis buffer flush statistics
- Kafka schedule processing (processed/notified/failed)
- Consumer lag monitoring
- JVM heap usage and HikariCP connection pool

### Structured Logging

ECS (Elastic Common Schema) JSON logging via Spring Boot 3.4+ native support for production log aggregation (ELK/Loki).

---

## Complexity Analysis

### API Operations

| Operation | Time Complexity | Space Complexity | Notes |
|---|---|---|---|
| Device registration | O(1) | O(1) | IMEI uniqueness check via indexed column |
| Heartbeat processing | O(1) | O(1) | Redis HSET (sub-ms), flushed to PG every 30s |
| Version publishing | O(1) | O(1) | Version code uniqueness via unique index |
| Compatibility rule creation | O(1) | O(1) | Insert with uniqueness constraint |
| BFS upgrade path check | O(V + E) | O(V) | V = versions, E = rules. Queue + visited set |
| Schedule creation | O(D) | O(D) | D = number of target devices matched by filters |
| Schedule approval | O(1) | O(1) | Publishes to Kafka and returns immediately |
| State transition | O(1) | O(1) | Single record update with transition validation |
| Dashboard summary | O(D + S) | O(1) | D = device count query, S = schedule count query. Cached (5min TTL) |
| Audit trail query | O(A) | O(A) | A = number of audit events for the entity |
| Device listing (paginated) | O(log D + P) | O(P) | B-tree index seek + P results per page |

### Database Indexes

All primary lookups use indexed columns:
- `devices.imei` — unique index, O(log N) lookup
- `app_versions.version_code` — unique index
- `version_compatibility(from_version_code, to_version_code)` — composite unique
- `device_updates.device_id` — foreign key index for pending update queries
- `audit_logs.entity_id` — index for timeline queries

### Scaling Considerations

| Scale Point | Current Design | At Scale (100K+ devices) |
|---|---|---|
| Device registration | Single PostgreSQL | Connection pooling handles ~200 concurrent registrations |
| Heartbeat flood | Redis write buffer | Batched to PostgreSQL every 30s — handles millions/hour |
| Schedule approval | Kafka async processing | Horizontal scaling via consumer groups + partitions |
| Dashboard queries | Cached 5 min | Add materialized views for version distribution |
| Audit log growth | Append-only table | Add table partitioning by month |
| BFS path finding | In-memory per request | Pre-compute adjacency list in Redis on rule change |

---

## Error Handling

Every error returns a consistent `ApiErrorResponse`:

```json
{
  "error": "DEVICE_NOT_FOUND",
  "message": "Device with IMEI 12345 not found",
  "timestamp": "2025-01-15T10:30:00"
}
```

| Exception | HTTP Status | Error Code |
|---|---|---|
| `DeviceNotFoundException` | 404 | `DEVICE_NOT_FOUND` |
| `DeviceAlreadyExistsException` | 409 | `DEVICE_ALREADY_EXISTS` |
| `VersionAlreadyExistsException` | 409 | `VERSION_ALREADY_EXISTS` |
| `DowngradeNotAllowedException` | 400 | `DOWNGRADE_NOT_ALLOWED` |
| `NoUpgradePathException` | 400 | `NO_UPGRADE_PATH` |
| `InvalidStateTransitionException` | 400 | `INVALID_STATE_TRANSITION` |
| `ScheduleNotFoundException` | 404 | `SCHEDULE_NOT_FOUND` |
| `BadCredentialsException` | 401 | `INVALID_CREDENTIALS` |
| Validation failures | 400 | `VALIDATION_ERROR` |
| Unhandled exceptions | 500 | `INTERNAL_ERROR` |

---

## ER Diagram

```
┌──────────────┐       ┌──────────────────────┐       ┌─────────────────┐
│    admins     │       │    app_versions       │       │ version_compat  │
├──────────────┤       ├──────────────────────┤       ├─────────────────┤
│ id (UUID PK) │       │ id (UUID PK)          │       │ id (UUID PK)    │
│ username     │       │ version_code (UNIQUE) │◄──────│ from_version    │
│ password     │       │ version_name          │◄──────│ to_version      │
│ role (ENUM)  │       │ release_date          │       │ requires_inter  │
│ created_at   │       │ min/max_os_version    │       │ inter_version   │
└──────┬───────┘       │ customization_tag     │       └─────────────────┘
       │               │ is_mandatory          │
       │               │ is_active             │
       │               │ created_at            │
       │               └──────────────────────┘
       │
       │ created_by
       ▼
┌──────────────────────┐        ┌─────────────────┐
│  update_schedules    │        │     devices      │
├──────────────────────┤        ├─────────────────┤
│ id (UUID PK)         │        │ id (UUID PK)    │
│ from_version_code    │        │ imei (UNIQUE)   │
│ to_version_code      │        │ app_version     │
│ target_region        │        │ device_os       │
│ target_client_tag    │        │ device_model    │
│ rollout_type (ENUM)  │        │ region          │
│ rollout_percentage   │        │ client_tag      │
│ status (ENUM)        │        │ last_heartbeat  │
│ scheduled_at         │        │ status (ENUM)   │
│ approved_by          │        │ created_at      │
│ approved_at          │        └────────┬────────┘
│ created_at           │                 │
└──────────┬───────────┘                 │
           │                             │
           │         ┌───────────────────┘
           │         │
           ▼         ▼
┌──────────────────────────┐         ┌──────────────────────┐
│     device_updates       │         │     audit_logs       │
├──────────────────────────┤         ├──────────────────────┤
│ id (UUID PK)             │         │ id (UUID PK)         │
│ schedule_id (FK)         │         │ entity_type (ENUM)   │
│ device_id (FK)           │         │ entity_id (UUID)     │
│ current_state (ENUM)     │         │ action               │
│ failure_stage            │         │ previous_value       │
│ failure_reason           │         │ new_value            │
│ retry_count              │         │ actor_id (UUID)      │
│ created_at               │         │ actor_type (ENUM)    │
│ updated_at               │         │ metadata (JSONB)     │
└──────────────────────────┘         │ created_at           │
                                     └──────────────────────┘
                                     (Append-only, no updates)
```

---

## Testing

### Automated Tests

| Test Suite | Coverage |
|---|---|
| `DeviceServiceTest` | Registration (success, duplicate IMEI), heartbeat Redis buffer, unknown IMEI, version compliance |
| `SchedulerServiceTest` | Inactive device detection, auto-retry logic, scheduled rollout trigger |
| `DeviceUpdateServiceTest` | All valid/invalid state transitions, downgrade prevention |
| `UpdateStateTest` | Exhaustive enum transition matrix (66 test cases) |
| `VersionCompatibilityServiceTest` | BFS upgrade path finding |
| `MdmSystemApplicationTests` | Spring context load verification |

### Manual E2E Testing (Postman)

Full lifecycle tested via Postman collection (`MDM-System.postman_collection.json`):

```
Login (3 roles) → Publish versions → Create compatibility rules
→ BFS path validation → Register devices → Heartbeat
→ Schedule update → Approve schedule (Kafka async)
→ Full state machine: NOTIFIED → ... → INSTALLATION_COMPLETED
→ Dashboard verification → Audit trail check
→ RBAC enforcement → Downgrade prevention
```

---

## Configuration

Key settings in `application.yaml`:

| Setting | Value | Purpose |
|---|---|---|
| `server.port` | 8081 | Application HTTP port |
| `spring.datasource.url` | `localhost:5433/mdm_db` | PostgreSQL connection |
| `spring.data.redis.port` | 6379 | Redis connection |
| `spring.kafka.bootstrap-servers` | `localhost:9092` | Kafka broker |
| `app.jwt.expiration` | 86400000 (24h) | JWT token lifetime |
| `spring.jpa.ddl-auto` | validate | Hibernate validates schema, doesn't modify it |
| `spring.jpa.open-in-view` | false | Prevents lazy loading outside transactions |
| `hikari.maximum-pool-size` | 20 | Max concurrent DB connections |
