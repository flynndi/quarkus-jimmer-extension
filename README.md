# Quarkus Jimmer Extension

# Note
Support Kotlin   
Kotlin has been supported since 0.0.1.CR7

Refer to [Jimmer](https://github.com/babyfish-ct/jimmer) for its data access APIs. This extension integrates them with Quarkus configuration, CDI, datasources, and transactions.

The GraphQL integration and its APT/KSP processors have been removed from this development version. GraphQL integration will be reconsidered separately.


# Quick Start
## Dependency
Gradle
```groovy
implementation 'io.github.flynndi:quarkus-jimmer:0.0.1.CR60'
annotationProcessor 'org.babyfish.jimmer:jimmer-apt:0.9.120'
```
Maven
```xml
<dependency>
   <groupId>io.github.flynndi</groupId>
   <artifactId>quarkus-jimmer</artifactId>
   <version>0.0.1.CR60</version>
</dependency>

<build>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
            <version>3.10.1</version>
            <configuration>
                <annotationProcessorPaths>
                    <path>
                        <groupId>org.babyfish.jimmer</groupId>
                        <artifactId>jimmer-apt</artifactId>
                        <version>0.9.120</version>
                    </path>
                </annotationProcessorPaths>
            </configuration>
        </plugin>
    </plugins>
</build>
```
## Java
### JPA
```java
// default db

// repository
public interface BookRepository extends JRepository<Book, Long> {

}

// service
@ApplicationScoped
public class BookService {

    @Inject
    BookRepository bookRepository;
    
    public Book findById(long id) {
        return bookRepository.findNullable(id);
    }
}

// if other databases exist

// repository
@DataSource("DB2")
public interface UserRoleRepository extends JRepository<UserRole, UUID> {

}

// service
@ApplicationScoped
public class UserRoleService {

    @Inject
    @DataSource("DB2")
    UserRoleRepository userRoleRepository;
    
    public UserRole findById(long id) {
        return userRoleRepository.findNullable(id);
    }
}
```

### Code
```java
    // Inject JSqlClient or static method Jimmer.getJSqlClient
    // default db
    @Inject
    JSqlClient jSqlClient;
    
    // if other databases exist
    @Inject
    @DataSource("DB2")
    JSqlClient jSqlClientDB2;
    
    public Book findById(int id) {
        return jSqlClient.findById(Book.class, id);
//  or  return Jimmer.getDefaultJSqlClient().findById(Book.class, id);    
    }
    
    public Book2 findById(int id) {
        return jSqlClientDB2.findById(Book2.class, id);
//  or  return Jimmer.getJSqlClient(DB2).findById(Book2.class, id);
    }
```

### Data sources and CDI

A Jimmer client uses its matching Quarkus Agroal datasource. Named datasources do not require a default datasource. `quarkus.jimmer.active=false` (or `quarkus.jimmer.<datasource-name>.active=false`) deactivates that client; an inactive datasource also deactivates its client and transaction cache operator. Use `InjectableInstance` and check the bean's active state when choosing between clients that may be inactive.

For single-valued CDI extension points such as `Dialect`, `ConnectionManager`, and `Consumer<JSqlClient.Builder>`, an explicit `@DataSource(name)` bean takes precedence over an ordinary `@Default` bean. This includes `@DataSource("<default>")` for the default datasource. If no datasource-qualified bean matches, the ordinary default bean is used. Ambiguous beans at the selected level fail resolution instead of silently selecting one or falling back. Collection extension points, such as filters and customizers, include global beans and beans for the matching datasource. Supported Jimmer extension-point beans are retained automatically; they do not need `@Unremovable`.

The extension's automatic `TransactionCacheOperator` belongs only to its CDI-managed client. Clients created manually with `SqlClients.java(...)` or `SqlClients.kotlin(...)` do not reuse that operator; configure a dedicated operator through the builder if the manually created client needs transaction-aware cache invalidation. User-provided operators remain supported, including ordinary `@Default` and the legacy `@DataSource("<default>")` form for the default datasource; multiple matching user operators are rejected. Named clients require a matching `@DataSource(name)` operator and do not fall back to the default datasource's operator, because an operator cannot be shared by multiple SQL clients.

### Cache

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
#### reference
Quarkus remote associations Depend on quarkus-rest-client-reactive-jackson   
Read the Quarkus-rest-client-reaction-jackson documentation before you begin   
https://quarkus.io/guides/rest-client-reactive

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
BookStoreRepository bookStoreRepository;

@GET
@Path("/path")
@Api
public Response testBookRepositoryViewById(@RestQuery long id) {
    return Response.ok(bookRepository.viewer(BookDetailView.class).findNullable(id)).build();
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
### JPA
```kotlin
// default db

// repository
@ApplicationScoped
class BookRepository : KRepository<Book, Long>

// service
@ApplicationScoped
class BookService {

    @Inject
    @field:Default
    lateinit var bookRepository: BookRepository

    fun findById (id : Long) : Book? {
        return bookRepository.findNullable(id)
    }
}

// if other databases exist

// repository
@ApplicationScoped
@DataSource("DB2")
class UserRoleRepository : KRepository<UserRole, UUID>

// service
@ApplicationScoped
@DataSource("DB2")
class UserRoleService {

    @Inject
    @field:DataSource("DB2")
    lateinit var userRoleRepository: UserRoleRepository

    fun findById(id : UUID) : UserRole? {
        return userRoleRepository.findNullable(id)
    }
}
```

### Code
```kotlin
    // Inject KSqlClient or static method Jimmer.getKSqlClient

    // default db
    @Inject
    @field: Default
    lateinit var kSqlClient: KSqlClient
    
    // if other databases exist
    @Inject
    @field:DataSource("DB2")
    lateinit var kSqlClientDB2: KSqlClient

    fun findById(id : Long) : Book? {
        return kSqlClient.findById(Book::class, id)
//  or  return Jimmer.getDefaultKSqlClient().findById(Book::class, id)
    }

    fun findById(id : Long) : Book2? {
        return kSqlClientDB2.findById(Book2::class, id)
//  or  return Jimmer.getKSqlClient("DB2").findById(Book2::class, id)
    }
```
