# Integration examples and tests

The examples below use the H2 test data. The default datasource shows books for tenant `a`. A second datasource named `DB2` holds user roles.

### Books

[BookResources](src/main/java/io/quarkiverse/jimmer/it/resource/BookResources.java) uses an injected [BookJavaRepository](src/main/java/io/quarkiverse/jimmer/it/repository/BookJavaRepository.java).

| Request | Expected result |
| --- | --- |
| `GET /bookResource/book/1` | The Fetcher returns *Learning GraphQL*, its store name and its authors' names. |
| `GET /bookResource/book/1/view` | The generated `BookDetailView` includes more detail, such as each author's gender. Both single-book queries return 204 for an unknown ID. |
| `GET /bookResource/books/page?index=0&size=2` | A page containing IDs 11 and 9, in descending order. Rows contain only the ID and name. |
| `GET /bookResource/books/search?name=graphql&minPrice=51&storeName=REILLY` | The specification matches book 3. You can also filter by `maxPrice` and `authorName`. |
| `POST /bookResource/book` with `{"id":8000001,"name":"Example Book","edition":1,"price":15,"tenant":"a"}` | Returns the saved book. Read it back at `/bookResource/book/8000001`. |
| `GET /bookResource/testError` | The test profile translates a generated exception into HTTP 500 with error family `USER_INFO` and code `ILLEGAL_USER_NAME`. |

[BookSaveTest](src/test/java/io/quarkiverse/jimmer/it/transaction/BookSaveTest.java) checks the committed row and the two rollback scenarios. [JimmerHttpTestCase](src/test/java/io/quarkiverse/jimmer/it/resource/JimmerHttpTestCase.java) checks the read responses, error translation, and generated client documentation.

### User roles on the named datasource

[UserRoleResources](src/main/java/io/quarkiverse/jimmer/it/resource/UserRoleResources.java) uses the [repository bound to DB2](src/main/java/io/quarkiverse/jimmer/it/repository/UserRoleJavaRepository.java). Use a fresh UUID for `{id}` and reuse it throughout the sequence. The HTTP test `createsUpdatesAndLogicallyDeletesAUserRoleOnTheNamedDatasource` exercises all six operations.

| Request | Expected result |
| --- | --- |
| `POST /userRoleResources` with `{"id":"{id}","userId":"reader","roleId":"editor"}` | Creates a role and returns `deleteFlag: false`. |
| `GET /userRoleResources/userRoleFindById?id={id}` | Returns the role stored on `DB2`. |
| `PUT /userRoleResources` with `{"id":"{id}","userId":"reader","roleId":"reviewer"}` | Updates the role. Reading it back returns `reviewer`. |
| `GET /userRoleResources/testUserRoleSpecification?userId=reader&roleId=reviewer` | Lists matching active roles. |
| `DELETE /userRoleResources/delete?id={id}` | Logically deletes the role. Its ID disappears from the active query above. |
| `GET /userRoleResources/deleteReverseById?id={id}` | Returns the same ID with `deleteFlag: true`. |

### Computed properties, recursion, and dynamic tables

| Request | Expected result | Source |
| --- | --- | --- |
| `GET /bookStoreResource/1` | O'REILLY has an average price of 53.1 and latest visible editions with IDs 3, 5, and 9. | [BookStoreResources](src/main/java/io/quarkiverse/jimmer/it/resource/BookStoreResources.java) |
| `GET /treeNodeResources/all?parentId=1` | The top-level results are Food and Clothing, each with recursive details. Omit `parentId` to return all 24 nodes. | [TreeNodeResources](src/main/java/io/quarkiverse/jimmer/it/resource/TreeNodeResources.java) |
| `GET /diagnostics/dynamic-tables?id=1` | The generated table and weak join resolve *Learning GraphQL* and O'REILLY, with `tableTypesMatch` set to `true`. | [DynamicTableResource](src/main/java/io/quarkiverse/jimmer/it/resource/DynamicTableResource.java) |

These three requests are covered by [the HTTP suite](src/test/java/io/quarkiverse/jimmer/it/resource/JimmerHttpTestCase.java). [JimmerHttpIT](src/test/java/io/quarkiverse/jimmer/it/resource/JimmerHttpIT.java) reuses the same suite against a packaged JVM process or native executable.

### Running the tests

Use a disposable Redis instance. Both Redis clients use database 15. Set `JIMMER_TEST_REDIS_URL` to the server URL without a database path, or use the default `redis://127.0.0.1:6379`. The test profile supplies both in-memory H2 databases.

Run the commands from the repository root.

```sh
# Install the extension and run deployment tests first.
mvn -pl deployment -am clean install -Dgpg.skip=true

# Run the application tests in the test JVM.
mvn -pl integration-tests test -Dgpg.skip=true

# Also build and test a separate JVM application process.
mvn -pl integration-tests -Ppackaged-tests verify -Dgpg.skip=true

# Run the same HTTP contract against a native image (requires a native toolchain or container build).
mvn -pl integration-tests verify -Dnative -Dnative.surefire.skip=true -Dgpg.skip=true
```

`application-test.yml` supplies the shared test configuration. The `packaged-tests` and native Maven profiles also select it during augmentation, so build-time settings match those used when Failsafe launches the application. Normal development continues to use the application's development configuration.

Keep new HTTP tests focused on distinct transport contracts. Verify API overloads and query syntax in deployment tests, and use independent data for every integration test that writes to the database.
