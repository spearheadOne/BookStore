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
- `S3Config` explicitly imports the shared `S3HealthIndicator` (`s3` in Actuator).
  It uses the service's existing S3 client and configured `s3.bucket` for a signed
  `HeadBucket` request. Only that probe gets a two-second total API-call timeout (including retries) and a one-second
  attempt timeout; object-transfer timeouts
  remain unchanged. Missing/inaccessible buckets and SDK/network/timeout failures
  report `DOWN` with sanitized details. The probe does not create buckets or read,
  list or write objects. AWS general-purpose buckets require bucket-level
  `s3:ListBucket` permission on `arn:aws:s3:::<bucket>`; see the
  [S3 HeadBucket documentation](https://docs.aws.amazon.com/AmazonS3/latest/API/API_HeadBucket.html).
  HeadBucket's generic error statuses do not reliably distinguish a missing bucket
  from denied access. Health detail visibility and health groups remain service-owned.
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

## Local service images

Both services use Java 25 from the Gradle toolchain, the Paketo Noble Java Tiny
builder (`paketobuildpacks/builder-noble-java-tiny:latest`), its matching
`paketobuildpacks/ubuntu-noble-run-tiny:latest` run image, and the Adoptium JRE
buildpack. `BP_JVM_VERSION` follows the toolchain in each module. The builder and
run image are a matching Ubuntu 24.04 pair; see the
[Paketo builder reference](https://paketo.io/docs/reference/builders-reference/).

With Docker running, build only the search image from the repository root:

```sh
./gradlew :search-service:bootBuildImage
```

The image convention is `abondar/bookstore-<service>:<project.version>`:
`abondar/bookstore-search-service:0.0.1` and
`abondar/bookstore-book-service:0.0.1`. To build the book image explicitly, use
`./gradlew :book-service:bootBuildImage`. Running the unqualified
`./gradlew bootBuildImage` at the root selects both services. Both tasks set
`publish = false`; the images are stored only in the local Docker daemon.

Kafka, OpenSearch, Keycloak and S3 addresses/credentials are runtime settings;
they are not passed to buildpacks or baked into the image. The root `.env`
is the shared base for credentials, issuer, buckets, topics and host-run settings.
The combined `docker/.env-example` contains non-secret Docker overrides for both
applications and can be used directly. Load `--env-file .env` first and
`--env-file docker/.env-example` second; later values override the shared base.
There are no separate per-service env files. Set `KEYCLOAK_AUDIENCE` per container.
The example targets the existing Compose network (`bookstore_default`): Kafka uses
`kafka:19092`, and OpenSearch uses `http://opensearch:9200`. Both containers listen
on `8080` (API) and `8001` (management). Book maps these to the same host ports;
search maps them to host ports `8082` and `8002` to avoid clashes. Host/IDE search
runs use `SERVER_PORT=8082` and `MANAGEMENT_SERVER_PORT=8002`.
A container's `localhost` refers to itself, rather than the host dependencies.

```sh
docker compose up -d kafka opensearch keycloak seaweedfs postgres redis
docker run --detach --name bookstore-book-service \
  --network bookstore_default --memory 2g \
  --env-file .env --env-file docker/.env-example \
  --env KEYCLOAK_AUDIENCE=book-service \
  --publish 127.0.0.1:8080:8080 --publish 127.0.0.1:8001:8001 \
  abondar/bookstore-book-service:0.0.1

docker run --detach --name bookstore-search-service \
  --network bookstore_default --memory 2g \
  --env-file .env --env-file docker/.env-example \
  --env KEYCLOAK_AUDIENCE=book-search \
  --publish 127.0.0.1:8082:8080 --publish 127.0.0.1:8002:8001 \
  abondar/bookstore-search-service:0.0.1

docker logs bookstore-search-service
curl --fail http://localhost:8002/actuator/health/kafka
curl --fail http://localhost:8002/actuator/health/opensearch
curl --fail http://localhost:8001/actuator/health/s3
curl --fail http://localhost:8002/actuator/health/s3
```

Both dependency checks should report `UP`. The shared root `.env` keeps
`KEYCLOAK_ISSUER_URI=http://localhost:8081/realms/bookstore`, matching the existing
Keycloak's token issuer. Docker overrides fetch signing keys and health discovery
through `keycloak:8080`. `KEYCLOAK_HEALTH_DISCOVERY_URI` changes only the probe's
network address: the returned discovery issuer must still match the configured
public issuer exactly. Connection/read timeouts remain two seconds; probes do not
request tokens or use administrative credentials. Without an override, host runs
probe the issuer's discovery endpoint as before. Both apps use the existing shared
Keycloak. Public API documentation is at `http://localhost:8082/swagger-ui.html`.

Stop and remove the application containers when finished:

```sh
docker stop bookstore-book-service bookstore-search-service
docker rm bookstore-book-service bookstore-search-service
```
