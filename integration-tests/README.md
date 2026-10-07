# Integration tests

The suite covers real SQL, Redis, CDI events, transaction rollback and HTTP serialization.
Repository parsing and API combinations live in `deployment/src/test`, where they do not depend on this example application.
`JimmerHttpIT` reuses the pure HTTP tests against the packaged JVM application or native executable.

Use a disposable Redis instance. Both Redis clients use database 15; `JIMMER_TEST_REDIS_URL` accepts a server URL without a database path and defaults to `redis://127.0.0.1:6379`.
The tests use separate in-memory H2 databases and clean up their own SQL records and Redis binder keys.

From the repository root:

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
