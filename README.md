# Quarkus Jimmer Extension

# Note
Support Kotlin   
Kotlin has been supported since 0.0.1.CR7

Refer to [Jimmer](https://github.com/babyfish-ct/jimmer) for its data access APIs. This extension integrates them with Quarkus configuration, CDI, datasources, and transactions.

The GraphQL integration and its APT/KSP processors have been removed from this development version. GraphQL integration will be reconsidered separately.


# Quick Start
## Dependency

These examples target the current source branch with Jimmer `0.12.3`. Its POM currently uses extension version `0.0.1.CR60`; build and install this branch locally to use these changes. The already published CR60 artifact retains its original dependencies and does not include this upgrade. Keep the runtime and entity processor versions aligned.

Gradle
```groovy
def quarkusJimmerVersion = '0.0.1.CR60'
def jimmerVersion = '0.12.3'

implementation "io.github.flynndi:quarkus-jimmer:${quarkusJimmerVersion}"
annotationProcessor "org.babyfish.jimmer:jimmer-apt:${jimmerVersion}"
```
Maven
```xml
<properties>
    <quarkus-jimmer.version>0.0.1.CR60</quarkus-jimmer.version>
    <jimmer.version>0.12.3</jimmer.version>
</properties>

<dependencies>
<dependency>
   <groupId>io.github.flynndi</groupId>
   <artifactId>quarkus-jimmer</artifactId>
   <version>${quarkus-jimmer.version}</version>
</dependency>
</dependencies>

<build>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
            <version>3.13.0</version>
            <configuration>
                <annotationProcessorPaths>
                    <path>
                        <groupId>org.babyfish.jimmer</groupId>
                        <artifactId>jimmer-apt</artifactId>
                        <version>${jimmer.version}</version>
                    </path>
                </annotationProcessorPaths>
            </configuration>
        </plugin>
    </plugins>
</build>
```
## Java

### Repository as a CDI bean

For new code, write a concrete CDI class extending `io.quarkiverse.jimmer.runtime.repo.support.AbstractJavaRepository`. Its constructor receives the SQL client. The base class implements `JavaRepository`; extending it is optional when direct `JSqlClient` access is sufficient.

Use `@Singleton` for these subclasses: the base classes have no no-argument constructor for ArC to generate a normal-scope client proxy. CDI injection and `@Transactional` interception remain available.

```java
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Singleton;
import jakarta.inject.Inject;
import org.babyfish.jimmer.sql.JSqlClient;
import io.quarkiverse.jimmer.runtime.repo.support.AbstractJavaRepository;

@Singleton
public class BookRepository extends AbstractJavaRepository<Book, Long> {

    @Inject
    public BookRepository(JSqlClient sql) {
        super(sql);
    }
}
```

Use the repository through ordinary CDI injection. `findById` returns `null` when absent; `save` returns a Jimmer save result. Put transaction boundaries on your application service or repository methods with Quarkus `@Transactional`.

```java
@ApplicationScoped
public class BookService {

    @Inject
    BookRepository books;

    public Book findById(long id) {
        return books.findById(id);
    }

    @jakarta.transaction.Transactional
    public Book save(Book book) {
        return books.save(book).getModifiedEntity();
    }
}
```

For a named datasource, qualify the injected client in the repository constructor:

```java
import java.util.UUID;
import io.quarkus.agroal.DataSource;

@Singleton
public class UserRoleRepository extends AbstractJavaRepository<UserRole, UUID> {

    @Inject
    public UserRoleRepository(@DataSource("DB2") JSqlClient sql) {
        super(sql);
    }
}
```

This remains a normal CDI repository bean. The constructor's `@DataSource` chooses its client; consumers can inject `UserRoleRepository` without repeating that qualifier.

### Direct SQL client access

Injecting `JSqlClient` is also a supported application entry point. A repository is not required for Jimmer queries, fetchers, DTOs, or save commands.

```java
@ApplicationScoped
public class BookQueries {

    @Inject
    JSqlClient sql;

    @Inject
    @DataSource("DB2")
    JSqlClient otherSql;

    public Book findById(long id) {
        return sql.findById(Book.class, id);
    }

    public UserRole findRoleById(UUID id) {
        return otherSql.findById(UserRole.class, id);
    }
}
```

New `JavaRepository` / `KotlinRepository` interfaces do not trigger implementation or method-name query generation. Implement custom queries with Jimmer's DSL in your CDI class. See [repository migration and public contracts](docs/modules/ROOT/pages/repository-migration.adoc).

### Data sources and CDI

Prefer injecting `JSqlClient` or `KSqlClient`, with `@DataSource("name")` for a named datasource. CDI-managed clients are application-scoped: ArC creates the underlying client lazily when its proxy is first used. Each datasource has one managed client, and the compatibility client containers expose that same CDI proxy.

Injecting a client proxy alone does not initialize it or validate its active state; calling an inactive client fails on first use. Construction failures are reported as CDI creation errors with the underlying cause preserved.

A Jimmer client uses its matching Quarkus Agroal datasource. Named datasources do not require a default datasource. `quarkus.jimmer.active=false` (or `quarkus.jimmer.<datasource-name>.active=false`) deactivates that client; an inactive datasource also deactivates its client and transaction cache operator. Use `InjectableInstance` and check the bean's active state when choosing between clients that may be inactive.

For single-valued CDI extension points such as `Dialect`, `ConnectionManager`, and `Consumer<JSqlClient.Builder>`, an explicit `@DataSource(name)` bean takes precedence over an ordinary `@Default` bean. This includes `@DataSource("<default>")` for the default datasource. If no datasource-qualified bean matches, the ordinary default bean is used. Ambiguous beans at the selected level fail resolution instead of silently selecting one or falling back. Collection extension points, such as filters and customizers, include global beans and beans for the matching datasource. Supported Jimmer extension-point beans are retained automatically; they do not need `@Unremovable`.

SPI beans may inject the matching client for later use. During construction, `@PostConstruct`, `customize`, or `initialize`, do not call back through the injected proxy if that client is being created. An `Initializer` should use the client passed to its callback.

Only the selected Java or Kotlin filters, customizers, and initializers are instantiated. Connection and dialect defaults are completed after Jimmer executes the user customizers. A customizer's explicit dialect avoids JDBC dialect probing; if it replaces the connection manager, dialect detection uses the replacement.

`SqlClients.java(...)` and `SqlClients.kotlin(...)` build independent clients immediately; construction failures are reported by the factory call. The extension's automatic `TransactionCacheOperator` belongs only to its CDI-managed client. Manual clients do not reuse that operator; configure a dedicated operator through the builder if the manually created client needs transaction-aware cache invalidation. User-provided operators remain supported, including ordinary `@Default` and the legacy `@DataSource("<default>")` form for the default datasource; multiple matching user operators are rejected. Named clients require a matching `@DataSource(name)` operator and do not fall back to the default datasource's operator, because an operator cannot be shared by multiple SQL clients.

### Transactions

Jimmer's `TxConnectionManager.executeTransaction(...)` uses Quarkus Narayana's `@Transactional` semantics for all six propagation modes: `REQUIRED`, `REQUIRES_NEW`, `SUPPORTS`, `NOT_SUPPORTED`, `MANDATORY`, and `NEVER`. Participating calls leave transaction completion to their caller; suspended transactions are restored when the callback returns or throws.

This is a synchronous JDBC boundary. Returning a future or publisher as a value does not extend the transaction beyond the callback. Narayana's rollback rules apply, including Quarkus `@Rollback` annotations on exception types. See [transaction behavior](docs/modules/ROOT/pages/index.adoc#transactions) for the propagation matrix and failure contracts.

### Optional integrations and configuration

The extension uses Quarkus's lightweight Scheduler by default. Applications that need Quartz can add `io.quarkus:quarkus-quartz` explicitly; Quarkus then selects it for the existing scheduled job.

REST, HTTP endpoints, and the default microservice HTTP exchange are optional. Add `quarkus-rest-jackson` for REST exception translation, `quarkus-vertx-http` (or an extension that brings it in) for document endpoints, and `quarkus-rest-client` plus HTTP support for the microservice bridge. These dependencies are no longer supplied transitively by Jimmer. Jackson support remains a separate core dependency; REST Jackson supplies the HTTP JSON writer for translated errors.

REST exception translation is disabled by default. Set `quarkus.jimmer.error-translator.disabled=false` explicitly to enable it; configuring its status code or debug options alone does not enable the integration.

Document endpoints are opt-in: set `quarkus.jimmer.client.ts.path`, `quarkus.jimmer.client.openapi.path`, or `quarkus.jimmer.client.openapi.ui-path` to expose them. The previous default OpenAPI/UI URLs are no longer registered automatically. Relative endpoint paths follow Quarkus's non-application root; absolute paths retain their explicit location. A UI needs either a generated specification path or an explicit `ref-path`.

Configuration errors report the complete property keys at build time or runtime initialization, without opening database connections or eagerly creating clients. The checks use Quarkus configuration APIs and do not require Hibernate Validator. See [optional integrations and validation](docs/modules/ROOT/pages/index.adoc#optional-integrations) for the dependency and migration contracts.

### Cache

Transaction cache invalidation is associated with the JTA transaction, including suspended transactions and transactions resumed on another thread. A transaction that emits Jimmer database events schedules one flush after successful commit; rollback does not trigger it. Only operators for the datasources involved are flushed, each in its own new transaction after Agroal releases the completed transaction's connection.

A cache failure cannot undo an already committed business transaction. If cache deletion throws, its flush transaction rolls back and the durable invalidation records remain for scheduled retry. Cache deletion must be idempotent because retries can repeat it. Events outside a JTA transaction rely on scheduled retry. See [cache completion behavior](docs/modules/ROOT/pages/index.adoc#transaction-cache) for the full contract.

The retry interval defaults to `5s`. Setting `quarkus.jimmer.transaction-cache-operator-fixed-delay=off` disables this retry job while preserving commit callbacks; failed or nontransactional invalidations then have no periodic retry. This does not disable the application's other scheduled jobs.

example: [CacheConfig.java](integration-tests%2Fsrc%2Fmain%2Fjava%2Fio%2Fquarkiverse%2Fjimmer%2Fit%2Fconfig%2FCacheConfig.java)   
use blocking RedisDataSource 

The difference with spring integration is need 

ValueCommands and HashCommands 

quarkus-jimmer-extension static methods are provided

```java
ValueCommands<String, byte[]> stringValueCommands = RedisCaches.cacheRedisValueCommands(redisDataSource);

HashCommands<String, String, byte[]> stringHashCommands = RedisCaches.cacheRedisHashCommands(redisDataSource);
```

```java
@ApplicationScoped
public class CacheConfig {

    @Singleton
    @Unremovable
    public CacheFactory cacheFactory(RedisDataSource redisDataSource, ObjectMapper objectMapper) {

        ValueCommands<String, byte[]> stringValueCommands = RedisCaches.cacheRedisValueCommands(redisDataSource);

        HashCommands<String, String, byte[]> stringHashCommands = RedisCaches.cacheRedisHashCommands(redisDataSource);

        return new AbstractCacheFactory() {
            @Override
            public Cache<?, ?> createObjectCache(ImmutableType type) {
                return new ChainCacheBuilder<>()
                        .add(new CaffeineBinder<>(512, Duration.ofSeconds(1)))
                        .add(new RedisValueBinder<>(stringValueCommands, objectMapper, type, Duration.ofMinutes(10)))
                        .build();

            }

            @Override
            public Cache<?, ?> createAssociatedIdCache(ImmutableProp prop) {
                return createPropCache(
                        getFilterState().isAffected(prop.getTargetType()),
                        prop,
                        stringValueCommands,
                        stringHashCommands,
                        objectMapper,
                        Duration.ofMinutes(5));
            }

            @Override
            public Cache<?, List<?>> createAssociatedIdListCache(ImmutableProp prop) {
                return createPropCache(
                        getFilterState().isAffected(prop.getTargetType()),
                        prop,
                        stringValueCommands,
                        stringHashCommands,
                        objectMapper,
                        Duration.ofMinutes(5));
            }

            @Override
            public Cache<?, ?> createResolverCache(ImmutableProp prop) {
                return createPropCache(
                        prop.equals(BookStoreProps.AVG_PRICE.unwrap()),
                        prop,
                        stringValueCommands,
                        stringHashCommands,
                        objectMapper,
                        Duration.ofHours(1));
            }
        };
    }

    private static <K, V> Cache<K, V> createPropCache(
            boolean isMultiView,
            ImmutableProp prop,
            ValueCommands<String, byte[]> stringValueCommands,
            HashCommands<String, String, byte[]> stringHashCommands,
            ObjectMapper objectMapper,
            Duration redisDuration) {
        if (isMultiView) {
            return new ChainCacheBuilder<K, V>()
                    .add(new RedisHashBinder<>(stringHashCommands, stringValueCommands, objectMapper, prop, redisDuration))
                    .build();
        }

        return new ChainCacheBuilder<K, V>()
                .add(new CaffeineBinder<>(512, Duration.ofSeconds(1)))
                .add(new RedisValueBinder<>(stringValueCommands, objectMapper, prop, redisDuration))
                .build();
    }
}
```

### Remote Associations

Add `io.quarkus:quarkus-rest-client` and HTTP server support such as `io.quarkus:quarkus-vertx-http` or `io.quarkus:quarkus-rest`. The bridge uses the configured Jackson mapper directly, so it does not require the REST Client Jackson provider. Setting `quarkus.jimmer.micro-service-name` enables the default HTTP exchange and export endpoints; missing required extensions produce a configuration error.

#### application.yml
```yaml
quarkus:
  application:
    name: Your application name
  jimmer:
    micro-service-name: ${quarkus.application.name}
  rest-client:
    other-service:  # Target service name
      url: http://localhost:8888 
    good-service:   # Target service name
      url: http://localhost:9090
```
#### current service entity
```java
// The entity of the current service
@Entity(microServiceName = "Your application name")
public interface Book {}
```
#### target service entity
```java
// Target service entity 
// "other-service" is the service name configured under the rest-client node in the application.yml file
@Entity(microServiceName = "other-service")
public interface BookStore {}
```
#### start query
[TestResources.java](integration-tests%2Fsrc%2Fmain%2Fjava%2Fio%2Fquarkiverse%2Fjimmer%2Fit%2Fresource%2FTestResources.java)
```java
@Inject
BookRepository bookRepository;

@GET
@Path("/path")
@Api
public Response testBookRepositoryViewById(@RestQuery long id) {
    return Response.ok(bookRepository.findById(id, BookDetailView.class)).build();
}
```

### Configuration file example
quarkus datasource documentation https://quarkus.io/guides/datasource
```yml
# Configuration file example
quarkus:
  jimmer:           # jimmer config see https://github.com/babyfish-ct/jimmer
    show-sql: true
    pretty-sql: true
    inline-sql-variables: true
    trigger-type: TRANSACTION_ONLY
    database-validation:
      mode: NONE
    error-translator:
      disabled: false
      debug-info-supported: true
    client:
      ts:
        path: /Code/ts.zip
      openapi:
        path: /openapi.yml
        ui-path: /openapi.html
        properties:
          info:
            title: Jimmer REST Example(Java)
            description: This is the OpenAPI UI of Quarkus-Jimmer-Extension REST Example (Java)
            version: latest
          securities:
            - tenantHeader: [1, 2, 3]
            - oauthHeader: [4, 5, 6]
          components:
            securitySchemes:
              tenantHeader:
                type: apiKey
                name: tenant
                in: HEADER
              oauthHeader:
                type: apiKey
                name: tenant
                in: QUERY
```
## Kotlin

### Repository as a CDI bean

Select Kotlin clients with `quarkus.jimmer.language=kotlin`. Generate your entity model with the Jimmer KSP processor matching the runtime version; the removed GraphQL processors are unrelated to Jimmer's entity generation.

```kotlin
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Singleton
import jakarta.inject.Inject
import org.babyfish.jimmer.sql.kt.KSqlClient
import io.quarkiverse.jimmer.runtime.repo.support.AbstractKotlinRepository

@Singleton
class BookRepository @Inject constructor(sql: KSqlClient) :
    AbstractKotlinRepository<Book, Long>(sql)

@ApplicationScoped
class BookService @Inject constructor(private val books: BookRepository) {

    fun findById(id: Long): Book? = books.findById(id)

    @jakarta.transaction.Transactional
    fun save(book: Book): Book = books.save(book).modifiedEntity
}
```

Choose a named client on the constructor parameter:

```kotlin
import java.util.UUID
import io.quarkus.agroal.DataSource

@Singleton
class UserRoleRepository @Inject constructor(
    @param:DataSource("DB2") sql: KSqlClient
) : AbstractKotlinRepository<UserRole, UUID>(sql)
```

### Direct SQL client access

```kotlin
@ApplicationScoped
class BookQueries @Inject constructor(private val sql: KSqlClient) {

    fun findById(id: Long): Book? = sql.findById(Book::class, id)
}
```

## Compatibility and migration

The older `io.quarkiverse.jimmer.runtime.repository.JRepository` and `KRepository` APIs remain a compatibility surface for existing applications. Their generated implementations and derived method names belong to that legacy path. New code should use the CDI classes or direct clients shown above.

| Existing repository API | New repository API |
| --- | --- |
| `findById(id)` returns `Optional<E>` | `findById(id)` returns nullable `E` / `E?` |
| `findNullable(id)` returns nullable `E` | Use `findById(id)` |
| `save(entity)` returns the modified entity | Returns `SimpleSaveResult<E>` / `KSimpleSaveResult<E>`; read `modifiedEntity` when needed |
| `findAll(new Pagination(index, size))` | `findPage(PageParam.byIndex(index, size))` |
| Derived methods such as `findByName(...)` | Write a method with Jimmer DSL in the concrete CDI class |
| Declaring an interface triggers legacy generation | New repository interfaces require an application implementation |

`JavaRepository`, `KotlinRepository`, `PageParam`, and the two `runtime.repo.support.Abstract*Repository` base classes are public application contracts. The base classes are intentionally supported for inheritance despite their `support` package. Repository metadata, parsers, bytecode generators, and other implementation helpers are internal, not application SPIs.

See the [migration guide](docs/modules/ROOT/pages/repository-migration.adoc) for concrete before/after examples, pagination indexing, and transaction guidance.
