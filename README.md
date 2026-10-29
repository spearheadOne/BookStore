# BookStore
short ddd and hexagonal arch demo

## API documentation

The bookstore service serves Swagger UI at `http://localhost:8080/swagger-ui.html` and its source OpenAPI contract at
`http://localhost:8080/openapi.yaml`. Swagger UI reads that contract. The shared Gradle configuration packages each
service's `src/main/openapi/openapi.yaml` into its own build output; generated sources remain under `build/`.
