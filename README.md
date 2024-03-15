# Quarkus Jimmer Extension

# Note
In most cases you can refer to jimmer's configuration of spring, there is no real difference between the two, only a few differences   
Refer to: https://github.com/babyfish-ct/jimmer   

TODO: GraphQL, Remote Associations   
Please let me know if you have any suggestions on these two parts


# Quick Start

## dependency
Gradle:
```groovy
implementation 'io.github.flynndi:quarkus-jimmer:0.0.1.CR4'
annotationProcessor 'org.babyfish.jimmer:jimmer-apt:0.8.108'
```
Maven
```maven
<dependency>
   <groupId>io.github.flynndi</groupId>
   <artifactId>quarkus-jimmer</artifactId>
   <version>0.0.1.CR4</version>
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
                        <version>0.8.108</version>
                    </path>
                </annotationProcessorPaths>
            </configuration>
        </plugin>
    </plugins>
</build>
```

# JPA
```
// default db

// repository
@ApplicationScoped
public class BookRepository implements JRepository<Book, Long> {

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
@ApplicationScoped
@DataSource("DB2")
public class UserRoleRepository implements JRepository<UserRole, UUID> {

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

# Code
```
    // default db
    @Inject
    JSqlClient jSqlClient;
    
    // if other databases exist
    @Inject
    @DataSource("DB2")
    JSqlClient jSqlClientDB2;
    
    public Book findById(int id) {
        return jSqlClient.findById(Book.class, id);
    or  return Jimmer.getDefaultJSqlClient().findById(Book.class, id);    
    }
    
    public Book2 findById(int id) {
        return jSqlClientDB2.findById(Book2.class, id);
    or  return Jimmer.getJSqlClient(DB2).findById(Book2.class, id);
    }
    
    Inject JSqlClient or static method Jimmer.getJSqlClient
```

# Cache

example: /src/main/java/io/quarkiverse/jimmer/it/config/CacheConfig.java
use blocking RedisDataSource 

The difference with spring integration is need 

ValueCommands and HashCommands 

quarkus-jimmer-extension static methods are provided

```
ValueCommands<String, byte[]> stringValueCommands = RedisCaches.cacheRedisValueCommands(redisDataSource);

HashCommands<String, String, byte[]> stringHashCommands = RedisCaches.cacheRedisHashCommands(redisDataSource);
```

```
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

# Configuration file example
quarkus datasource documentation https://quarkus.io/guides/datasource
```
# Configuration file example
quarkus:
  package:
    type: uber-jar
  http:
    port: 8080
  datasource:       default db
    db-kind:
    username:
    password:
    jdbc:
      min-size: 2
      max-size: 8
      url:
    DB2:            other db
      db-kind:
      username:
      password:
      jdbc:
        min-size: 2
        max-size: 8
        url:
  log:
    level: DEBUG
  jimmer:           jimmer config see https://github.com/babyfish-ct/jimmer
    show-sql:
    pretty-sql:
    inline-sql-variables:
    trigger-type:
    database-validation:
      mode:
        client:
      ts:
        path: /youPath/ts.zip
      openapi:
        path: /openapi.yml
        ui-path: /openapi.html
        properties:
          info:
            title: Jimmer REST Example(Java)
            description: This is the OpenAPI UI of Jimmer REST Example(Java)
            version: 0.0.1.CR2
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

# Version
preview build by

| Depend  | Version |    | Future |
|---------|---------|----|--------|
| JDK     | 11      | -> | 17     |
| Quarkus | 3.6.4   | -> | 3.8.0  |
| Jimmer  | 0.8.100 | -> | latest |

# Feature
| Feature               |     |
|-----------------------|-----|
| Multiple data sources | yes |
| JDBC                  | yes |
| Reactive              | no  |