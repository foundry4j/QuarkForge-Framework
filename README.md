# QuarkForge

<img src="docs/logo.svg" alt="QuarkForge logo" align="right" width="128" height="128" />

![Java 27+](https://img.shields.io/badge/Java-27%2B-ED8B00?logo=openjdk&logoColor=white)
[![Quarkus 3.40](https://img.shields.io/badge/Quarkus-3.40-blue)](https://img.shields.io/badge/Quarkus-3.40-blue)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.foundry4j/quarkforge-core?logo=apachemaven&color=blue)](https://central.sonatype.com/artifact/io.github.foundry4j/quarkforge-core)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=foundry4j_QuarkForge&metric=coverage)](https://sonarcloud.io/summary/new_code?id=foundry4j_QuarkForge)
[![CI](https://github.com/foundry4j/QuarkForge/actions/workflows/ci.yml/badge.svg)](https://github.com/foundry4j/QuarkForge/actions/workflows/ci.yml)

A modular rapid microservice development framework based on Quarkus, with built-in everyday development fundamentals such as unified responses, exception handling, data access, security authentication, rate limiting, Redis, and observability.

## Module Structure

| Module | Responsibility | Core Contents |
|--------|----------------|---------------|
| `quarkforge-bom` | Version alignment | Single import that locks the Quarkus platform and all framework module versions |
| `quarkforge-core` | Infrastructure (pure JDK, no framework dependencies) | `Result` unified response body, `ErrorCode` error codes, `BizException`, `PageResult`, `Checks` validation, `SnowflakeIdGenerator` |
| `quarkforge-web` | Web layer | Global exception mapping, automatic response wrapping, `@RawResponse` exemption, access log with traceId, `PageQuery` pagination parameters, file upload/download, API versioning |
| `quarkforge-gateway` | API gateway | In-process reverse proxy with declarative routes, longest-prefix matching, prefix stripping, timeouts, and unified 502/504 `Result` bodies |
| `quarkforge-validation` | Parameter validation | Business-oriented Bean Validation annotations: `@Phone`, `@IdCard`, `@BankCard`, `@PasswordStrength`, `@EnumValid` |
| `quarkforge-data` | Data access | `BaseEntity` (snowflake ID + audit timestamps), `BaseRepository` (pagination straight to `PageResult`), `TenantEntity` multi-tenant auto-filtering, `SoftDeleteEntity`/`SoftDeleteRepository` soft delete with recycle bin, `AuditSupport` audit SPI |
| `quarkforge-audit` | Audit logging | Automatically records create/update/delete operations with field-level diffs on updates (e.g. price 100 → 120), `AuditLog` persistence, operator resolution, `@OperationLog` method-level operation logs |
| `quarkforge-crypto` | Encryption & masking | AES-256-GCM `CryptoService`, `EncryptedStringConverter` field encryption, `@Sensitive` response masking (`138****0000`) |
| `quarkforge-cache` | Local cache | `CacheService` SPI, Caffeine default implementation, `@Cacheable`/`@CacheEvict` annotations, `@CacheWarmup` startup preloading, built-in Bloom-filter penetration guard and single-flight breakdown guard |
| `quarkforge-dict` | Dictionary & metadata | Multi-tenant dict types/items, cached lookups, value→label translation, hierarchical dictionaries via `parentValue` |
| `quarkforge-excel` | Excel / CSV import & export | Streaming xlsx/CSV reading, row-level validation with error rows, SXSSF streaming export, REST upload/download helpers |
| `quarkforge-migration` | Database migration | Flyway starter that automatically runs versioned scripts under `db/migration` at application startup |
| `quarkforge-security` | Security & authentication | `JwtTokenService` token issuance (jti, roles + `perms` permission claims), `TokenBlacklist` SPI (jti and user-level revocation enforced by a security identity augmentor), `AuthContext` current-user resolution, `@RequiresRole`/`@RequiresPermission` RBAC annotations with ANY/ALL semantics, `LoginUser` |
| `quarkforge-auth` | Password login | `PasswordEncoder` (BCrypt default / Argon2 optional), login, rotating refresh tokens stored as SHA-256 hashes, refresh/logout endpoints |
| `quarkforge-ratelimit` | Rate limiting | `@RateLimit` annotation, in-memory fixed-window default implementation, `RateLimiter` SPI |
| `quarkforge-degrade` | Fault tolerance | SmallRye FT `@Retry`/`@CircuitBreaker`/`@Timeout`/`@Bulkhead`/`@Fallback`, circuit-open/timeout/bulkhead exceptions mapped to unified `Result` (503/504) |
| `quarkforge-idempotency` | Idempotency | `@Idempotent` annotation, expression-based idempotency keys, cached result replay, `IdempotentStore` SPI (in-memory default / Redis auto-takeover) |
| `quarkforge-messaging` | Message pub/sub | `MessageEnvelope` message envelope, `MessagePublisher` SPI, `JsonMessagePublisher` direct object publishing, `@MessageConsumer` + `MessageHandler` consumer abstraction (local in-JVM dispatch by default) |
| `quarkforge-messaging-rabbitmq` | RabbitMQ | Exchange declaration, routing-key delivery, persistent messages, optional publisher confirms, consumer with durable queues and manual ack/nack |
| `quarkforge-messaging-kafka` | Kafka | Kafka transport for messaging: topic delivery with messageId as record key, headers/timestamp mapping, per-topic consumer group with manual offset commit |
| `quarkforge-outbox` | Transactional outbox | Local message table, delayed delivery, exponential-backoff retry on failure, scheduled redelivery |
| `quarkforge-rpc` | RPC communication | gRPC infrastructure: `BizException` ↔ gRPC status mapping with error-code trailers, traceId propagation interceptors, built-in `Echo` diagnostics service |
| `quarkforge-scheduler` | Job scheduling | JobRunr integration with in-memory storage (standalone) and SQL storage (distributed), built-in dashboard |
| `quarkforge-notification` | Notification center | Multi-tenant notification persistence, `NotificationSender` SPI for business modules to decouple from delivery concerns, REST API, and WebSocket real-time push |
| `quarkforge-redis` | Redis integration | `RedisCacheService` cache-aside, `RedisTwoLevelCacheService` local + Redis two-level cache, `RedisLockService` distributed locks with `@DistributedLock` annotation (SpEL keys, wait/retry, lease), Redis backends for rate limiting, idempotency and token blacklisting (auto-takeover) |
| `quarkforge-observability` | Observability | OpenAPI/Swagger UI (built-in bearerAuth scheme), `/q/health` health checks, `/q/metrics` Prometheus metrics |
| `quarkforge-test` | Test utilities | `TestAuth` SecurityIdentity mocking (roles/permissions aligned with `AuthContext`), `@CleanDatabase` JUnit 5 extension wiping all JPA entities before each test |

Modules are pulled in on demand with no forced coupling; `core` has no Quarkus extension dependencies and can be used standalone in non-web scenarios.

## Getting Started

Import the BOM and the modules you need. Choose your build tool:

**Gradle (Kotlin DSL)**

```kotlin
// settings.gradle.kts — import the platform
dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

// build.gradle.kts
dependencies {
    implementation(platform("io.quarkus.platform:quarkus-bom:3.40.1.1"))
    implementation(platform("io.github.foundry4j:quarkforge-bom:1.0.1"))

    implementation("io.github.foundry4j:quarkforge-web")
}
```

**Maven**

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>io.quarkus.platform</groupId>
            <artifactId>quarkus-bom</artifactId>
            <version>3.40.1.1</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
        <dependency>
            <groupId>io.github.foundry4j</groupId>
            <artifactId>quarkforge-bom</artifactId>
            <version>1.0.1</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <dependency>
        <groupId>io.github.foundry4j</groupId>
        <artifactId>quarkforge-web</artifactId>
    </dependency>
</dependencies>
```

Resource classes can then return business objects directly:

```java
@Path("/users")
public class UserResource {

    @GET
    public Map<String, Object> hello() {
        // Automatically wrapped as {"code":200,"message":"success","data":{"msg":"hi"}}
        return Map.of("msg", "hi");
    }

    @GET
    @Path("/{id}")
    public User get(@RestPath Long id) {
        // Throw an exception when not found; globally mapped to 404 + unified response body
        return userRepository.requireById(id);
    }
}
```

## Module Usage

### Unified Responses & Exceptions (core + web)

- Returned POJOs are automatically wrapped in the `Result` structure; annotate with `@RawResponse` to skip wrapping (file downloads, external callbacks, etc.).
- Throwing `BizException(CommonErrorCode.NOT_FOUND)` in business code yields a `404 {"code":40400,...}` response.
- Parameter validation failures (`@NotBlank`, `@Valid`) are automatically aggregated into a 400 response.
- Every request gets a `traceId` written to the logging MDC and the `X-Trace-Id` response header.

### Internationalization (core + web + validation)

Responses are localized from the `Accept-Language` header. Error texts live in
`i18n/messages*.properties` (`core`), validation texts in `ValidationMessages*.properties` (`validation`):

| Request | `BizException(CommonErrorCode.NOT_FOUND)` message |
|---------|---------------------------------------------------|
| `Accept-Language: en` | The requested resource does not exist |
| `Accept-Language: zh-CN`, or no header | 请求的资源不存在 |

- Only the error text is translated: `code` never changes, so clients can keep switching on it.
- Messages passed explicitly (`new BizException(code, "库存不足")`) are returned untouched.
- Override any text by shipping your own `i18n/messages_<lang>.properties`, or replace the whole source
  with `I18n.setMessageSource(...)` (database, config center, ...).
- Outside an HTTP request: `I18n.message(CommonErrorCode.NOT_FOUND, Locale.ENGLISH)`; the fallback for
  unsupported languages is configurable via `I18n.setDefaultLocale(...)` (defaults to `zh_CN`).
- Custom validation annotations should use `{key}` messages (`String message() default "{my.app.rule}"`)
  so the same mechanism applies.

### Parameter Validation (validation)

Adds business-oriented validation annotations on top of standard Bean Validation (`@NotBlank` / `@Size`, etc.). Validation failures are mapped by the web layer to `400 {"code":40000,"message":"field error-message"}`.

```java
public class RegisterRequest {

    @NotBlank
    public String username;

    @Phone                                // 11 digits starting with 1[3-9]
    public String phone;

    @IdCard                               // 18 digits + birth date + weighted check digit
    public String idCard;

    @BankCard                             // 12-19 digits + Luhn checksum
    public String bankCard;

    @PasswordStrength(min = 8, requireSpecial = true)
    public String password;

    @EnumValid(UserStatus.class)          // Must be an enum constant name; ignoreCase supported
    public String status;
}
```

All annotations follow the convention "`null` is valid; leave required-ness to `@NotNull`". `@Phone`/`@IdCard`/`@BankCard`/`@PasswordStrength` apply to string fields; `@EnumValid` applies to string fields with a target enum specified.

### File Upload & Download (web)

`FileStorage` is the storage SPI; the default implementation writes to the local disk (partitioned by `yyyy/MM/dd`, filenames rewritten as UUIDs, path traversal prevented).

```java
@Inject FileStorage storage;

// Multipart upload
@POST
@Consumes(MediaType.MULTIPART_FORM_DATA)
public StoredFile upload(@RestForm FileUpload file) {
    return FileUploads.store(storage, file);
}

// Streaming download; non-ASCII filenames are RFC 5987 encoded so browsers display them correctly
@GET
@Path("/{id}/content")
public Response download(@RestPath Long id) {
    FileRecord record = repository.requireById(id);
    return FileDownloads.download(storage, record.key(), record.filename(), record.contentType());
}
```

Built-in protection: exceeding the size limit throws `413 {"code":41300}`, a MIME type outside the whitelist throws `415 {"code":41500}`, and an illegal storage key throws 400; partially written files are cleaned up automatically on write failure.

```properties
quarkforge.web.file.base-dir=${java.io.tmpdir}/quarkforge-files
quarkforge.web.file.max-size=10M
quarkforge.web.file.allowed-content-types=image/png,application/pdf   # empty means unrestricted
```

To integrate object storage, implement `FileStorage` and register it as a regular bean to override the default.

### API Versioning (web)

Declare supported versions with `@ApiVersion`; callers specify the version via the `X-API-Version` header (or the first URL segment `/v1/...`):

```java
@Path("/orders")
@ApiVersion("v1")
public class OrderResource {

    @GET
    public PageResult<OrderVO> list() { ... }

    @GET
    @Path("/legacy")
    @ApiVersion(value = "v1", deprecated = true, sunset = "2027-01-01", successor = "/v2/orders")
    public List<OrderVO> legacyList() { ... }
}
```

- Requested version not in the supported list → `400 {"code":40001,...}`
- Responses always write back `X-API-Version`
- Deprecated endpoints additionally return `Deprecation: true`, `Sunset: 2027-01-01`, `Link: </v2/orders>; rel="successor-version"`
- Endpoints without `@ApiVersion` are not validated, keeping backward compatibility; version numbers `1` and `v1` are equivalent

After the filter resolves the version, it can be read by injecting `ApiVersionContext`:

```java
@Inject ApiVersionContext versionContext;
String version = versionContext.requested();
```

### API Gateway (gateway)

A lightweight in-process gateway: declarative routes forward matching requests to upstream services, with no extra component to deploy.

```properties
quarkforge.gateway.routes[0].id=order
quarkforge.gateway.routes[0].path=/api/order
quarkforge.gateway.routes[0].upstream=http://order-service:8080
quarkforge.gateway.routes[0].strip-prefix=0
quarkforge.gateway.routes[0].timeout=3s
```

- `/api/order/42` → `http://order-service:8080/42`: the matched prefix is removed, and `strip-prefix` removes further leading segments
- Longest prefix wins; hop-by-hop headers (`Host`, `Connection`, `Content-Length`, ...) are not forwarded
- Upstream timeout → `504 {"code":50400,...}`; unreachable upstream → `502 {"code":50300,...}`
- Routes are static (rebuilt on restart); request and response bodies are buffered in memory, so avoid large file transfers through this module

### Data Access (data)

```java
@Entity
public class User extends BaseEntity {   // Automatically gets snowflake id / createdAt / updatedAt
    public String name;
}

@ApplicationScoped
public class UserRepository extends BaseRepository<User> {
    public PageResult<User> search(String keyword, PageQuery query) {
        return page(find("name like ?1", "%" + keyword + "%"), query.page(), query.size());
    }
}
```

In multi-instance deployments, distinguish snowflake nodes via `quarkforge.data.worker-id`.

### Multi-Tenancy & Soft Delete (data)

```java
// Extend TenantEntity: writes auto-fill tenant_id; queries and load-by-id automatically carry the tenant condition
@Entity
public class Order extends TenantEntity { }

// Scheduled jobs / admin tasks can run across tenants
TenantContext.runAs("tenant-a", () -> orderRepository.listAll());

// Extend SoftDeleteEntity + SoftDeleteRepository
@Entity
public class Article extends SoftDeleteEntity { }

@ApplicationScoped
public class ArticleRepository extends SoftDeleteRepository<Article> { }

article.delete();                              // Only sets the flag; hidden from default listings
articleRepository.findAllIncludingDeleted();   // Recycle bin
articleRepository.restoreById(id);             // Restore
articleRepository.purgeById(id);               // Physical delete
```

When no tenant is set explicitly, the default tenant `default` is used (`quarkforge.data.tenant.default`). The request entry point can call `TenantContext.set(...)` to propagate the tenant according to the project's authentication scheme.

### Audit Logging (audit)

Adding `quarkforge-audit` takes effect automatically with no business code changes:

```java
@Inject AuditLogRepository auditLogRepository;

List<AuditLog> logs = auditLogRepository.findByEntity(Order.class.getName(), orderId);
// action=UPDATE, actor=bob, changes={"changes":[{"field":"price","oldValue":"100","newValue":"120"}]}
```

- The operator is resolved from `ActorContext` first, then the current `SecurityIdentity`, and finally `system`
- Audit records are persisted in the same transaction as business data; both roll back together
- `@NotAudited` skips specified entities; JPQL / native bulk updates do not trigger lifecycle callbacks and are therefore not audited

#### Operation Log

Annotate REST resource or service methods with `@OperationLog` to persist one record per invocation. Failure records are written in an independent transaction, so they survive business rollback:

```java
@POST
@OperationLog(module = "order", action = "create", description = "create order")
public Order createOrder(@Valid CreateOrderRequest req) { ... }

@Inject OperationLogRepository operationLogRepository;

PageResult<OperationLogEntry> page = operationLogRepository.pageByActor("alice", 1, 20);
PageResult<OperationLogEntry> failures = operationLogRepository.pageFailures(1, 20);
```

Each record contains the operator, method, arguments (JSON), success flag, error message, duration, traceId, and—when `quarkforge-web` is present—request method, path, client IP (`X-Forwarded-For` / `X-Real-IP`) and User-Agent.

- `recordArgs = false` skips argument persistence for sensitive payloads
- `quarkforge.audit.operation-log.enabled` toggles operation logging

### Field Encryption & Masking (crypto)

```properties
# Base64 of a 32-byte key; generate with openssl rand -base64 32
quarkforge.crypto.key=...
```

```java
@Convert(converter = EncryptedStringConverter.class)
@Sensitive(SensitiveType.PHONE)
public String phone;   // Ciphertext in the database, "138****0000" in API responses
```

Built-in rules: `PHONE` / `ID_CARD` / `EMAIL` / `BANK_CARD` / `NAME` / `PASSWORD` / `DEFAULT`; you can also call `Masking.mask(value, type)` directly.

### Database Migration (migration)

After adding `quarkforge-migration`, versioned scripts run automatically at startup:

```text
src/main/resources/db/migration/
├── V1__create_order.sql
└── V2__add_order_note.sql
```

Defaults are `migrate-at-start=true` and `baseline-on-migrate=true`; override them in the application's `application.properties`.

### Job Scheduling (scheduler)

Built on JobRunr 9 (native support for Quarkus 3.40 LTS). It runs locally with zero configuration, and adding a database automatically turns it into a distributed scheduler.

```java
@Inject SchedulerService scheduler;

// Fire-and-forget (a static method reference matches JobLambda and is serializable by JobRunr)
scheduler.enqueue(TicketService::processDailyReports);

// Run once at a specific instant
scheduler.scheduleAt(Instant.now().plus(Duration.ofMinutes(30)),
        OrderService::sendClosingReminder);

// Standard 6-field cron schedule
scheduler.scheduleCron("0 0 2 * * *", BillingService::runNightlySettlement);
scheduler.scheduleCron("0 0/5 * * * *", "refresh-metrics", MetricsService::refreshCaches);

// Fixed-interval recurring job (JobRunr does not distinguish fixed-delay / fixed-rate)
scheduler.scheduleRecurrently(Duration.ofSeconds(30), Heartbeat::send);
```

**Automatic switching between local and distributed modes**:

- **Standalone mode (default)**: without a DataSource, the `@DefaultBean InMemoryStorageProvider` provided by [StorageProviderProducer](quarkforge-scheduler/src/main/java/io/github/foundry4j/quarkforge/scheduler/StorageProviderProducer.java) takes effect, and the BackgroundJobServer polls and executes jobs within the same JVM. Suitable for development and single-node deployments.

- **Distributed mode**: once any DataSource (H2 / Postgres / MySQL) is present, the JobRunr Quarkus extension automatically detects it and registers a `SqlStorageProvider`. All nodes share the same `jobrunr_*` tables and use optimistic locking for mutual exclusion — **in a multi-node deployment each job is executed by exactly one node**. This is JobRunr's core advantage: lighter than a hand-rolled Redis lock or a Quartz cluster table.

**Common configuration** (`quarkus.jobrunr.*` prefix; override in the application's application.properties):

```properties
# The background job server is enabled by default; tune the poll interval as needed
quarkus.jobrunr.background-job-server.poll-interval=5   # seconds (15-30 recommended in production)

# Retry and job retention
quarkus.jobrunr.jobs.default-number-of-retries=3
quarkus.jobrunr.jobs.delete-succeeded-jobs-after=36h
quarkus.jobrunr.jobs.permanently-delete-deleted-jobs-after=72h

# The dashboard is disabled by default; enable it explicitly and secure it in production
quarkus.jobrunr.dashboard.enabled=false
quarkus.jobrunr.dashboard.port=8000
```

> When passing method references or lambdas, make sure the target **can be serialized by JobRunr**:
> - Static method references (e.g. `MyService::myStaticMethod`) ✅ safest — the background runner invokes them directly via reflection
> - Instance method references (e.g. `myBean::doWork`) require the CDI bean instance to be resolvable through ArC, so make sure the bean is registered
> - Lambda expressions capture the enclosing class reference, so deserialization requires that class to be on the classpath

### Caching (cache / redis)

`CacheService` SPI backed by Caffeine by default. Every local cache gets three anti-anomaly protections out of the box when used through the declarative annotations or through `LocalCacheService.computeProtected`:

- **Bloom filter** — short-circuits known-miss keys before the cache query
- **Single-flight gate** — concurrent requests on the same key share one loader call
- **Null-value caching** — loader returning `null` is written as a short-lived sentinel to stop penetration

```java
@Inject CacheService cache;

// SPI usage — no protection
cache.getOrLoad("user:" + id, User.class, Duration.ofMinutes(10), () -> load(id));

// Declarative — protection enabled automatically on LocalCacheService
@Cacheable(key = "#p0", namespace = "user", ttl = "PT10M")
public User getUser(long id) { ... }

@CacheEvict(key = "#p0", namespace = "user")          // after method success
public void updateUser(long id, UserPatch patch) { ... }
```

Supported key expressions inside the annotation (no SpEL dependency):

| Form | Meaning |
|------|---------|
| `'literal'` | String literal |
| `#p0`, `#p1`, … | Positional method argument |
| `#p0.field` | Bean property of the argument |
| `#p0.method()` | Zero-arg method on the argument |
| `#root.methodName` | Name of the annotated method |
| `'prefix/' + #p0` | Concatenation (split on `+` and rejoin) |
| (empty) | Default = `ClassName#methodName(args)` |

Annotation attributes:

| Attribute | Default | Purpose |
|-----------|---------|---------|
| `key` | auto | Cache key expression |
| `namespace` | `""` | Namespace prefix (`qf:cache:{ns}:{key}`) |
| `ttl` | `PT5M` | ISO-8601 duration; overrides `quarkforge.cache.local.default-ttl` |
| `cacheNull` | `true` | Cache loader `null` with short TTL (`quarkforge.cache.local.null-value-ttl`) |
| `preventPenetration` | `true` | Use Bloom filter (only `LocalCacheService`) |
| `preventBreakdown` | `true` | Use single-flight gate (only `LocalCacheService`) |

Configuration:

```properties
quarkforge.cache.local.max-size=10000
quarkforge.cache.local.default-ttl=PT5M
quarkforge.cache.local.null-value-ttl=PT1M          # short TTL for negative cache
quarkforge.cache.local.bloom.enabled=true
quarkforge.cache.local.bloom.expected-insertions=100000
quarkforge.cache.local.bloom.false-positive-rate=0.01
```

After adding `quarkforge-redis`, the default bean switches to `RedisTwoLevelCacheService`; two-level cache inherits the SPI but the Bloom / single-flight protections stay at the local layer, which is usually sufficient for hot-key shielding while Redis handles distribution.

The interceptor degrades gracefully when the active `CacheService` is not a `LocalCacheService` — it still caches through `getOrLoad`, but bloom + single-flight are skipped.

#### Cache warm-up

Annotate a no-arg method with `@CacheWarmup` and the framework invokes it after the application has started (`StartupEvent`), so hot keys are already populated before the first request arrives:

```java
@Unremovable      // production builds remove beans that are never injected
@ApplicationScoped
public class ProductWarmup {

    @Inject ProductService products;

    @CacheWarmup(name = "hot-products", order = 100)
    public void warm() {
        products.hotIds().forEach(products::requireById);   // fills @Cacheable entries
    }
}
```

| Attribute | Default | Purpose |
|-----------|---------|---------|
| `name` | `Class#method` | Task name used in logs |
| `order` | `0` | Smaller runs first |
| `enabled` | `true` | Skip an individual task |

```properties
quarkforge.cache.warmup.enabled=true      # global switch
quarkforge.cache.warmup.fail-fast=false   # true aborts startup when a task fails
```

Warm-up methods must be no-arg and their return values are ignored. Calls go through the CDI proxy, so `@Cacheable` / `@Transactional` interceptors apply. A failing task is logged and the remaining tasks still run unless `fail-fast` is enabled.

Because a warm-up bean is only referenced by the annotation, mark it `@Unremovable` so Quarkus keeps it in production builds (dev mode and tests never remove beans).

### Dictionary & Metadata (dict)

Stores enum-like reference data (gender, order status, units, …) so business tables keep only a short value while the UI renders a localized label. Every dictionary is tenant-scoped, cached, and read through the `CacheService` SPI.

```java
@Inject DictService dict;

List<DictItemView> statuses = dict.items("order_status");        // enabled items, ordered by sort
String label = dict.label("gender", "M");                        // "Man", also works for disabled items
Map<String, String> mapping = dict.translate("order_status");    // value → label
```

Manage dictionaries over REST:

| Method | Path | Description |
|--------|------|-------------|
| GET | `/dict/types?keyword=&enabled=&page=&size=` | Page through dictionary types |
| POST | `/dict/types` | Create a dictionary type |
| PUT | `/dict/types/{id}` | Rename/disable a type (the code is immutable) |
| DELETE | `/dict/types/{id}` | Delete a type together with its items |
| GET | `/dict/{typeCode}/items?enabled=false` | List items, optionally including disabled ones |
| POST | `/dict/{typeCode}/items` | Add an item |
| PUT | `/dict/items/{id}` | Update an item |
| DELETE | `/dict/items/{id}` | Delete an item |
| GET | `/dict/{typeCode}/translate` | Full value → label mapping |

Notes:

- `(tenant_id, code)` and `(tenant_id, type_code, item_value)` are unique, so duplicate codes or values return `409 {"code":40900}`.
- Reads are cached per tenant; any write through `DictService` evicts both the enabled-only and the full view, so callers never see stale labels.
- `parentValue` supports hierarchical dictionaries (province → city, category → subcategory).
- Cache TTL: `quarkforge.dict.cache-ttl=PT10M` (default 10 minutes).
- Enabled dictionary types are preloaded at startup via `@CacheWarmup`; set `quarkforge.dict.warmup.enabled=false` to disable. Only the current tenant is preloaded — other tenants load on first access.

### Excel / CSV Import & Export (excel)

Both directions are streaming — memory usage does not grow with the number of rows. Reading uses the POI SAX event model for `.xlsx` and a character-level parser for CSV (quoted commas, embedded newlines, `""` escapes, UTF-8 BOM). Export uses `SXSSFWorkbook` with a sliding row window, flushing to temporary files as it goes.

```java
List<User> users = new ArrayList<>();
ImportResult result = ExcelImporter.importData(in, ExcelFormat.XLSX,
        ExcelImportSpec.<User>builder()
                .mapper(row -> new User(
                        row.cell("id"),
                        row.cell("name"),
                        Integer.parseInt(row.cell("age"))))
                .validator((user, ctx) -> {
                    if (user.name().isBlank()) {
                        ctx.reject("name", user.name(), "name is required");
                    }
                    if (user.age() < 1 || user.age() > 120) {
                        ctx.reject("age", String.valueOf(user.age()), "age out of range");
                    }
                })
                .rowSink(users::add)     // successful rows are consumed one by one
                .maxErrors(500)
                .build());
```

Mapping or validation failures never abort the whole file. `ImportResult` carries `totalRows`, `successRows`, `failedRows`, `errors`, and `errorsTruncated`; each `RowError` contains `rowNumber`, `field`, `value`, `message`, and the original `rowValues`, so the client can highlight or re-export the offending rows.

```java
ExcelExporter.write(out, ExcelFormat.XLSX,
        ExcelExportSpec.<Order>builder("Orders")
                .column("Order No", Order::orderNo)
                .column("Amount", Order::amount, 16)
                .windowSize(100)
                .build(),
        orderStream);
```

REST helpers stream straight to the response and avoid buffering the whole file:

```java
@GET
@Path("/export")
public Response export() {
    return ExcelHttp.download("orders.xlsx", spec, orderService.streamAll());
}

@POST
@Path("/import")
@Consumes(MediaType.MULTIPART_FORM_DATA)
public ImportResult importUsers(@RestForm FileUpload file) {
    return ExcelHttp.importUpload(file, spec);
}
```

Notes:

- Supported inputs: `.xlsx` / `.xlsm` and `.csv`; legacy `.xls` must be converted first.
- CSV defaults to UTF-8; configure another charset with `ExcelImportSpec.Builder#charset`.
- `maxErrors(0)` means unlimited error collection — use it only for small files.
- The export window (`windowSize`) trades temporary disk usage for memory; 100 rows is a good default.

### Security & Authentication (security)

```java
@Inject JwtTokenService jwt;
@Inject AuthContext auth;

// Issue a token at the login endpoint
String token = jwt.issue(user.id.toString(), user.name, Set.of("admin"));

// Read the current user in business endpoints; 401 is thrown automatically when not logged in
LoginUser current = auth.currentUser();
```

Signing/verification keys use the standard SmallRye JWT configuration (RSA key files recommended; never commit private keys in production):

```properties
smallrye.jwt.sign.key.location=jwt/privateKey.pem
mp.jwt.verify.publickey.location=jwt/publicKey.pem
mp.jwt.verify.issuer=quarkforge
```

#### RS256 + JWKS key rotation

For asymmetric signing with `kid`-based rotation, configure a key ring instead of a single key. The active key signs new tokens; all configured public keys are published as a JWK Set at `/.well-known/jwks.json` (served by `quarkforge-auth`):

```properties
quarkforge.jwt.signing.active-kid=rsa-2026-11

quarkforge.jwt.signing.keys[0].id=rsa-2026-11
quarkforge.jwt.signing.keys[0].algorithm=RS256
quarkforge.jwt.signing.keys[0].private-key-location=classpath:keys/rsa-2026-11.pem
quarkforge.jwt.signing.keys[0].public-key-location=classpath:keys/rsa-2026-11.pub.pem

# keep the previous key for tokens that are still valid
quarkforge.jwt.signing.keys[1].id=rsa-2026-05
quarkforge.jwt.signing.keys[1].private-key-location=classpath:keys/rsa-2026-05.pem
quarkforge.jwt.signing.keys[1].public-key-location=classpath:keys/rsa-2026-05.pub.pem
```

Resource servers verify tokens by fetching that endpoint:

```properties
mp.jwt.verify.publickey.location=http://auth-service/.well-known/jwks.json
mp.jwt.verify.publickey.algorithm=RS256
mp.jwt.verify.issuer=quarkforge
```

Rotation steps: add the new key and switch `active-kid`, restart/roll out, keep the old key configured until its tokens expire, then remove it. `RS256/384/512`, `PS*` and `ES*` algorithms are supported; symmetric algorithms keep using `smallrye.jwt.sign.key`.

#### RBAC Authorization (@RequiresRole / @RequiresPermission)

Declare role/permission requirements on resource classes or methods; the framework validates them before the method runs:

```java
@Path("/admin/users")
@RequiresRole("admin")                       // class-level: every method requires the admin role
public class UserAdminResource {

    @GET
    @RequiresPermission("user:list")         // role check passes AND the user:list permission is required
    public Result<List<UserVO>> list() { ... }

    @DELETE
    @Path("/{id}")
    @RequiresRole(value = {"admin", "ops"}, logical = Logical.ALL)  // method-level overrides the class-level role rule
    @RequiresPermission("user:delete")
    public Result<Void> delete(@PathParam("id") Long id) { ... }
}
```

- `value` accepts multiple entries combined with `logical = Logical.ANY` (default, any one suffices) or `Logical.ALL` (all required).
- A method-level `@RequiresRole` overrides the class-level one; `@RequiresPermission` follows the same rule. When a method declares both, role and permission are checked independently.
- Unauthenticated access throws `401 UNAUTHORIZED`; missing roles/permissions throw `403 FORBIDDEN` with the missing items in the message, mapped to the unified `Result` by the web layer.
- Roles come from the JWT `groups` claim and permissions from the `perms` claim. Issue tokens carrying both at login:

```java
String token = jwt.issue(user.id.toString(), user.name,
        Set.of("admin"),                       // roles
        Set.of("user:list", "user:delete"));   // permissions
```

- For programmatic checks inside business logic, use `auth.hasPermission("user:delete")` / `auth.currentUser().hasPermission(...)`; both return `false` for anonymous callers.
- The mapping between users, roles and permissions is owned by your business side (e.g. loaded from the database at login); the framework only carries and validates the claims.

### Password Login & Refresh Tokens (auth)

Adds the login half of the JWT story on top of `quarkforge-security`. Users stay in your own database: implement `UserAccountProvider` and the framework exposes the endpoints.

```java
@ApplicationScoped
public class DbUserAccountProvider implements UserAccountProvider {

    public Optional<UserAccount> findByUsername(String username) { ... }

    public Optional<UserAccount> findById(String userId) { ... }   // used when refreshing
}
```

| Method | Path | Description |
|--------|------|-------------|
| POST | `/auth/login` | username + password → access token and refresh token |
| POST | `/auth/refresh` | rotate the refresh token, returns a new token pair |
| POST | `/auth/logout` | revoke the given refresh token |

```properties
quarkforge.auth.password.algorithm=bcrypt       # or argon2
quarkforge.auth.refresh-token.ttl-days=30
smallrye.jwt.sign.key={"kty":"oct","k":"...","alg":"HS256"}   # PEM or JWK
```

- Only the SHA-256 hash of a refresh token is stored; the plaintext value is returned once in the login/refresh response
- Every refresh rotates the token and revokes the old one; reusing an already revoked token is treated as leakage and revokes all tokens of that user
- Logout revokes the refresh token and blacklists the current access token's `jti`, so the access token stops working immediately
- `AuthService.revokeAllTokens(userId)` invalidates every access token issued before the call and revokes all refresh tokens; call it after password changes or account bans
- Passwords are verified by hash prefix, so switching the default algorithm keeps existing BCrypt passwords working
- BCrypt works out of the box; Argon2 requires adding `org.bouncycastle:bcprov-jdk18on` to the application

### Rate Limiting (ratelimit)

```java
@POST
@RateLimit(permits = 5, windowSeconds = 60, key = KeyStrategy.USER)
public Result<Void> sendSmsCode() { ... }
```

Supports three dimensions: `USER` / `IP` / `GLOBAL`; excess requests get HTTP 429. The default in-memory implementation suits single-node deployments; adding `quarkforge-redis` automatically switches to Redis-based distributed rate limiting.

### Idempotency (idempotency)

```java
@POST
@Idempotent(key = "order:#req.orderNo", ttlSeconds = 600)
public Result<OrderVO> createOrder(@RequestBody CreateOrderRequest req) {
    // Duplicate requests with the same orderNo:
    // - The first request executes the business method and caches the return value for 600 seconds
    // - Within the TTL, the cached result is replayed without re-executing
    // - Concurrent requests with the same key get HTTP 409 (CONFLICT)
}
```

The `key` supports `#paramName` and `#p0` index references with chained `.field` access; when left empty, the key is generated from the serialized arguments. The default in-memory store suits single-node deployments; adding `quarkforge-redis` automatically switches to the distributed implementation. If the business method throws, the idempotency key is released immediately so later retries are allowed.

### Messaging (messaging + RabbitMQ / Kafka)

No middleware required by default: messages are only written to memory and logged (`LocalMessagePublisher`), so local development works out of the box; adding `quarkforge-messaging-rabbitmq` or `quarkforge-messaging-kafka` automatically switches to real delivery.

```properties
quarkforge.messaging.rabbitmq.uri=amqp://guest:guest@localhost:5672/
quarkforge.messaging.rabbitmq.exchange=quarkforge.topic
quarkforge.messaging.rabbitmq.exchange-type=topic
quarkforge.messaging.rabbitmq.publisher-confirms=true
```

```java
@Inject JsonMessagePublisher publisher;

publisher.publish("order.created", Map.of("orderNo", orderNo));
```

`destination` is used as the routing key; if `exchange` is left empty, the message goes to the default exchange (in which case `destination` is the queue name). Consumers can use Quarkus-native `@Incoming` or a RabbitMQ client directly.

For Kafka, add `quarkforge-messaging-kafka` instead; `destination` becomes the topic and `messageId` the record key (same-key ordering):

```properties
quarkforge.messaging.kafka.bootstrap-servers=localhost:9092
quarkforge.messaging.kafka.group-id=order-service
```

### Transactional Outbox (outbox)

Keep business writes and message sending in the same transaction, avoiding "order persisted but message never sent":

```java
@Transactional
public void createOrder(CreateOrderRequest request) {
    orderRepository.persist(order);
    outbox.enqueue("order.created", new OrderCreatedEvent(order.id, order.no));
}
```

Delayed delivery and failure retry:

```java
// Trigger an order-closing check 30 minutes later
outbox.enqueue("order.close-check", Map.of("orderNo", orderNo), Duration.ofMinutes(30));
```

A background job scans the local message table every 5 seconds for redelivery; failures back off exponentially as `30s → 60s → 120s …` and are marked `FAILED` after 5 attempts. In multi-instance deployments, messages are claimed via conditional updates, so each message is delivered by exactly one instance. For plain scheduled tasks, use Quarkus `@Scheduled` directly.

### RPC (rpc)

gRPC-based communication foundation for inter-service calls, built on `quarkus-grpc`. Service definitions live in `src/main/proto`; the Quarkus build generates blocking, async and Mutiny stubs.

**Server side**: implement the generated `*ImplBase` (or the Mutiny service interface) as a CDI bean annotated `@GrpcService` + `@Singleton`; it is exposed on the gRPC port (default 9000):

```java
@GrpcService
@Singleton
public class OrderGrpcService extends OrderGrpc.OrderImplBase {

    @Override
    public void get(GetRequest request, StreamObserver<GetResponse> responseObserver) {
        // Throw BizException directly — the global interceptor maps it to a gRPC status
        Order order = orderRepository.findByIdOptional(request.getId())
                .orElseThrow(() -> new BizException(CommonErrorCode.NOT_FOUND));
        responseObserver.onNext(toResponse(order));
        responseObserver.onCompleted();
    }
}
```

**Client side**: inject stubs with `@GrpcClient` and configure the channel:

```java
@GrpcClient("order")
OrderGrpc.OrderBlockingStub orderStub;
```

```properties
quarkus.grpc.clients.order.host=order-service
quarkus.grpc.clients.order.port=9000
```

**Unified error mapping**: a server-side `BizException` is converted to the matching gRPC status by its HTTP-aligned code segment (40400 → NOT_FOUND, 40100 → UNAUTHENTICATED, 42900 → RESOURCE_EXHAUSTED, …), with the original numeric code carried in the `x-error-code` trailer. Clients recover it with [RpcErrors](quarkforge-rpc/src/main/java/io/github/foundry4j/quarkforge/rpc/RpcErrors.java):

```java
try {
    orderStub.get(request);
} catch (StatusRuntimeException e) {
    throw RpcErrors.toBizException(e);   // back to BizException with the original error code
}
```

Unhandled server exceptions map to `INTERNAL` without leaking internal details in the status description. Note the mapping applies to exceptions thrown directly by the service method (ImplBase style, or Mutiny methods that throw synchronously) rather than failed `Uni`s.

**Trace propagation**: with tracing enabled (default), the client interceptor forwards the current MDC `traceId` as the `x-trace-id` metadata header, and the server interceptor restores it into MDC — RPC logs line up with HTTP logs under the same trace id.

**Diagnostics**: the built-in `Echo` service returns the message it receives plus the trace id observed on the server, handy for verifying connectivity and interceptor wiring after deployment.

### Notification Center (notification)

Drop-in notification infrastructure for microservices: multi-tenant persistence, a decoupled sender SPI, REST API for the current user, and WebSocket real-time push with offline compensation.

**Business module integration**: inject the SPI and fire notifications from anywhere — the module takes care of persistence and WebSocket delivery:

```java
@Inject NotificationSender notifier;

// When a task is assigned
notifier.send(assigneeId, NotificationType.TASK,
        "You have a new task", "Please handle it", currentUserId, "/tasks/" + taskId);

// System-wide announcement (actorId omitted)
notifier.sendSystem(userId, "Maintenance window", "Scheduled tonight 02:00-03:00");
```

The SPI lives in `quarkforge.notification.service.NotificationSender`; the default implementation is `NotificationService`, which persists the record and attempts a WebSocket push in the same transaction. Offline users get the notification on reconnection — the WebSocket endpoint auto-delivers all rows with `deliveredAt IS NULL` to newly connected clients.

**REST API** (all scoped to the authenticated user via `AuthContext`):

| Method | Path | Description |
|--------|------|-------------|
| GET | `/notifications` | Paginated list; `?unread-only=true` to filter |
| GET | `/notifications/unread-count` | Unread count |
| PATCH | `/notifications/{id}/read` | Mark a single notification read |
| PATCH | `/notifications/read-all` | Mark everything read (returns how many rows changed) |
| DELETE | `/notifications/{id}` | Delete a notification |

Responses auto-wrap with `Result<T>` by the web module. Forbidding someone else's notification is guarded by both the tenant filter and the explicit `recipientId` check.

**WebSocket real-time push**: the server exposes `/ws/notifications`. Clients should authenticate via the standard Quarkus Security flow — or pass `?userId=<id>` for trusted internal service-to-service scenarios. The server pushes a plain JSON envelope for each new notification:

```json
{
  "id": 1234567890,
  "type": "TASK",
  "title": "You have a new task",
  "content": "Please handle it",
  "link": "/tasks/42",
  "actorId": "u001",
  "createdAt": "2026-11-04T10:00:00Z"
}
```

Clients keep the connection alive by replying `pong` to the server's `ping` (or send their own; the WebSocket handler accepts both directions). On reconnect the server replays all notifications with `deliveredAt IS NULL`, so no notification is lost across brief disconnections.

**Multi-tenancy**: `Notification` extends `TenantEntity` — tenant_id is auto-filled from `TenantContext`, and all reads go through the Hibernate tenant filter, so tenant A cannot peek at tenant B's notifications.

### Redis (redis)

```java
// Cache-aside
User user = cache.getOrLoad("user:" + id, User.class, Duration.ofMinutes(10),
        () -> userRepository.requireById(id));

// Distributed lock
lock.withLock("order:pay:" + orderId, Duration.ofSeconds(30), () -> doPay());
```

### Observability (observability)

Works out of the box: Swagger UI at `/q/swagger-ui` (with a built-in JWT bearerAuth debugging scheme), health checks at `/q/health`, Prometheus metrics at `/q/metrics`.

## Example Services

`examples/mall-service` is a runnable e-commerce microservice that wires together web / validation / data / audit / cache / security / ratelimit / idempotency / observability.

### Mall Service

| Capability | Where it lands in the example |
|------------|-------------------------------|
| Product management | [ProductResource](examples/mall-service/src/main/java/io/github/foundry4j/quarkforge/sample/mall/product/ProductResource.java) + [ProductService](examples/mall-service/src/main/java/io/github/foundry4j/quarkforge/sample/mall/product/ProductService.java) + [ProductRepository](examples/mall-service/src/main/java/io/github/foundry4j/quarkforge/sample/mall/product/ProductRepository.java) |
| Inventory | [OrderService](examples/mall-service/src/main/java/io/github/foundry4j/quarkforge/sample/mall/order/OrderService.java) deducts stock atomically on order creation and restores it on cancellation; insufficient stock returns `40000` |
| Orders | [OrderResource](examples/mall-service/src/main/java/io/github/foundry4j/quarkforge/sample/mall/order/OrderResource.java) + `OrderService`; order items snapshot the product name and price at purchase time |
| Unified responses & validation | DTOs are wrapped automatically; `@PasswordStrength` / `@Phone` / price and stock constraints are mapped to `400` |
| JWT RBAC | `JwtTokenService` issues roles plus `perms`; `@RolesAllowed` and `@RequiresPermission` guard endpoints |
| Cache | Product detail uses `@Cacheable`; updates and stock changes use `@CacheEvict` |
| Idempotency | `POST /orders` uses `@Idempotent` keyed by user + `requestNo`, so retries return the first result |
| Operation logs | `@OperationLog` on product and order writes, including failed attempts; query via `GET /admin/operation-logs` |
| Docs & monitoring | Swagger UI, `/q/health`, `/q/metrics` |

Endpoint overview:

| Method | Path | Description |
|--------|------|-------------|
| POST | `/auth/register` | Register a buyer (password strength + optional phone validation) |
| POST | `/auth/login` | Log in to obtain a JWT (rate limited) |
| GET | `/users/me` | Current logged-in user |
| GET | `/products?keyword=&page=&size=` | Product search with pagination (public) |
| GET | `/products/{id}` | Product detail (public, cached) |
| POST | `/products` | Create a product; requires `product:write` |
| PUT | `/products/{id}` | Update a product; requires `product:write` |
| POST | `/orders` | Create an order (idempotent, deducts stock) |
| GET | `/orders/mine?page=&size=` | Current user's orders |
| GET | `/orders/{id}` | Order detail; owner or `ADMIN` only |
| POST | `/orders/{id}/cancel` | Cancel an order and restore stock |
| GET | `/admin/operation-logs?module=&actor=&page=&size=` | Query operation logs; requires `oplog:read` |

The service seeds an administrator (`admin / Admin@123456`) and two demo products on first startup. Enable the example module in the root `settings.gradle.kts` before running it:

```kotlin
include("examples:mall-service")
```

```bash
./gradlew :examples:mall-service:build -x test
./gradlew :examples:mall-service:quarkusDev
```

Tests:

```bash
./gradlew :examples:mall-service:test
```

Swagger UI is available at `http://localhost:8080/q/swagger-ui`; log in as the administrator to exercise the product and operation-log endpoints. The example uses in-memory H2 and demo JWT keys from the repository; replace both in production.

## Configuration

| Property | Default | Description |
|----------|---------|-------------|
| `quarkforge.web.response.auto-wrap` | `true` | Automatic response-body wrapping switch |
| `quarkforge.web.access-log.enabled` | `true` | Access log switch |
| `quarkforge.web.file.base-dir` | `${java.io.tmpdir}/quarkforge-files` | File storage root directory |
| `quarkforge.web.file.max-size` | `10M` | Per-file size limit; excess returns 413 |
| `quarkforge.web.file.allowed-content-types` | empty (unrestricted) | Allowed upload MIME types, comma-separated |
| `quarkforge.web.version.enabled` | `true` | API version handling switch |
| `quarkforge.web.version.default` | `v1` | Default version when none is specified |
| `quarkforge.rpc.trace.enabled` | `true` | gRPC traceId propagation switch (client + server interceptors) |
| `quarkforge.data.worker-id` | `0` | Snowflake ID node number ([0, 1023]) |
| `quarkforge.data.tenant.default` | `default` | Tenant ID used when none is set explicitly |
| `quarkforge.audit.enabled` | `true` | Audit log persistence switch |
| `quarkforge.audit.operation-log.enabled` | `true` | Operation log persistence switch |
| `quarkforge.crypto.key` | none (required) | Field encryption key, Base64 of 32 bytes |
| `quarkforge.cache.local.max-size` | `10000` | Maximum number of local cache entries |
| `quarkforge.cache.local.default-ttl` | `PT5M` | Default local cache TTL |
| `quarkforge.jwt.issuer` | `quarkforge` | JWT issuer |
| `quarkforge.jwt.ttl-minutes` | `120` | JWT validity (minutes) |
| `quarkforge.outbox.enabled` | `true` | Outbox background dispatch switch |
| `quarkforge.outbox.dispatch-interval` | `5s` | Outbox scan interval |
| `quarkforge.outbox.max-attempts` | `5` | Maximum delivery attempts per message |
| `quarkforge.outbox.backoff-seconds` | `30` | Initial failure backoff in seconds (doubles exponentially afterwards) |
