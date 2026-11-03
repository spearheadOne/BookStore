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
