# BookStore
short ddd and hexagonal arch demo

## Local Keycloak

Start Keycloak with `docker compose up -d keycloak` (or run `docker compose up -d`
for all local dependencies). The admin console is at http://localhost:8081/admin
with development credentials `book_auth` / `book_auth_pwd`. Override these on first startup
with `KEYCLOAK_ADMIN_USERNAME` and `KEYCLOAK_ADMIN_PASSWORD`.

The `bookstore` realm and `book-service` client are imported automatically on first
startup and persisted in a Docker volume. This follows the
[Keycloak container startup import](https://www.keycloak.org/server/containers) workflow.
The locally running book service already defaults to this realm's issuer;
override it with `KEYCLOAK_ISSUER_URI` if needed.

Get a development service token:

```sh
curl --fail --silent --show-error \
  http://localhost:8081/realms/bookstore/protocol/openid-connect/token \
  -d grant_type=client_credentials \
  -d client_id=book-service \
  -d client_secret=book-service-dev-secret
```

Use the returned `access_token` as `Authorization: Bearer <access_token>` when
calling the book service. The bundled credentials and `start-dev` mode are for
local development. Startup import skips an existing realm; later realm changes
must be applied through the admin console or Admin API.

## API documentation

The bookstore service serves Swagger UI at `http://localhost:8080/swagger-ui.html` and its source OpenAPI contract at
`http://localhost:8080/openapi.yaml`. Swagger UI reads that contract. The shared Gradle configuration packages each
service's `src/main/openapi/openapi.yaml` into its own build output; generated sources remain under `build/`.

## Modules and shared infrastructure

`book-service` and `search-service` are runnable Spring Boot applications.
`event-contracts` contains the shared Avro contracts. `common-infrastructure` is a
plain Java Gradle library (no Boot application, executable jar, or automatic
configuration registration). Run `./gradlew test` or `./gradlew build` to include
all four modules.

Each application class explicitly imports `S3Config`,
`KafkaHealthConfig`, and `KeycloakHealthIndicator` from `org.abondar.experimental.infrastructure`, outside
both services' component-scan packages. Each configuration enables only its own
infrastructure; adding the library dependency alone registers none of these shared beans.
Redis slice tests bootstrap their own test-only configuration to avoid loading
application-level infrastructure imports.

- `S3Config` binds the shared `s3.*` properties and owns the S3 client and HTTP
  transport lifecycle. Endpoint overrides remain optional; region, path-style
  access, connection timeout and API-call timeout remain configurable. Credentials
  continue to use the AWS SDK default provider chain. Each service retains its
  `BOOK_S3_*` environment bindings and bucket usage.
- `KafkaHealthConfig` creates and closes the AdminClient from the service's
  `KafkaAdmin` configuration and registers `kafkaHealthIndicator`. The shared
  adapter retains a 2000 ms broker request timeout and bounded 2000 ms future
  waits, restoring the thread interrupt flag on interruption. The health adapter
  and indicator are components explicitly imported by this configuration. Producer/consumer
  settings, topics, event adapters and search retry/DLT rules stay in their service.
- The explicitly imported `KeycloakHealthIndicator` component uses the owning
  service's JWT issuer URI. It probes only realm discovery, checks the returned
  issuer, and uses two-second connection/read timeouts; no token issuance or
  administrative credentials are involved.
- Each service explicitly imports `ResourceServerSecurityConfig`, which owns the single
  `SecurityFilterChain` bean: stateless JWT defaults and rate limiting after authorization.
  Common health and API-documentation public paths live in the shared configuration.
  A service can add public routes with a `String[] servicePublicPaths` bean; no bean
  is needed when there are no additional routes. Each service supplies its JWT converter
  and rate-limit filter as ordinary Spring beans. Servlet registration of the rate-limit filter is disabled
  so it runs only inside the security chain, after authorization.
  Both select `ApiRateLimitFilter.apiV1` for the current URL/method policy; a service
  can supply different path predicates and method-to-limiter mappings through the
  filter constructor. Named limiter settings remain in each service's `resilience4j.*`
  configuration. Additional public routes, audiences, roles, endpoint permissions and internal
  administration rules remain service-owned, including search's JWT role converter.

Health bean names/details are preserved. Health detail visibility, management
ports, liveness/readiness decisions and dependency-specific indicators (such as
OpenSearch) remain in the services. Shared Kafka and Keycloak health tests live in
`common-infrastructure`; domain code, ports, persistence and generated types
remain in their existing modules.
