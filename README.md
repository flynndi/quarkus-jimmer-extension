# Quarkus Jimmer Extension

# Note
Support Kotlin   
Kotlin has been supported since 0.0.1.CR7

In most cases you can refer to jimmer's configuration of spring, there is no real difference between the two, only a few differences   
Refer to: https://github.com/babyfish-ct/jimmer   

TODO: GraphQL  
Please let me know if you have any suggestions on these parts


# Quick Start
## Dependency
Gradle
```groovy
implementation 'io.github.flynndi:quarkus-jimmer:0.0.1.CR48'
annotationProcessor 'org.babyfish.jimmer:jimmer-apt:0.9.73'
```
Maven
```xml
<dependency>
   <groupId>io.github.flynndi</groupId>
   <artifactId>quarkus-jimmer</artifactId>
   <version>0.0.1.CR48</version>
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
                        <version>0.9.73</version>
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
