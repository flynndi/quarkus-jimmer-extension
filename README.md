# Quarkus Jimmer Extension

# Jimmer
https://github.com/babyfish-ct/jimmer

# Version
preview build by

| Depend  | Version |    | Future |
|---------|---------|----|--------|
| JDK     | 11      | -> | 17     |
| Quarkus | 3.6.4   | -> | 3.7.3  |
| Jimmer  | 0.8.77  | -> | 0.8.92 |

# Feature
| Feature               |     |
|-----------------------|-----|
| Multiple data sources | yes |
| JDBC                  | yes |
| Reactive              | no  |

# Usage
Gradle:
```groovy
implementation 'io.github.flynndi:quarkus-jimmer:Beta'
annotationProcessor 'org.babyfish.jimmer:jimmer-apt:0.8.92'
```
Maven
```maven
<dependency>
   <groupId>io.github.flynndi</groupId>
   <artifactId>quarkus-jimmer</artifactId>
   <version>Beta</version>
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
                        <version>0.8.92</version>
                    </path>
                </annotationProcessorPaths>
            </configuration>
        </plugin>
    </plugins>
</build>
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
        path:
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