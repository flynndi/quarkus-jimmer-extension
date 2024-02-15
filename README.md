# Quarkus Quarkus Jimmer Extension

# Jimmer
https://github.com/babyfish-ct/jimmer

# Feature
| Feature               |     |
|-----------------------|-----|
| Multiple data sources | yes |
| JDBC                  | yes |
| Reactive              | no  |

# Usage
Gradle:
```groovy
implementation 'io.github.flynndi:quarkus-jimmer:preview'
```
Maven
```maven
<dependency>
   <groupId>io.github.flynndi</groupId>
   <artifactId>quarkus-jimmer</artifactId>
   <version>preview</version>
</dependency>
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