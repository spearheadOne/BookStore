#!/usr/bin/env bash

# From the repository root: source ./e2e-setup.sh

KONG=http://localhost:8000
SEARCH_ADMIN=http://localhost:8082/internal/api/v1/indexes
ISSUER=http://localhost:8081/realms/bookstore

curl -fsS http://localhost:8100/status/ready || return 1
curl -fsS http://localhost:8082/readyz || return 1

token() {
  curl -fsS --user "$1:$2" \
    --data 'grant_type=client_credentials' \
    "$ISSUER/protocol/openid-connect/token" | jq -er '.access_token'
}

BOOK_TOKEN=$(token book-service book-service-dev-secret) || return 1
SEARCH_TOKEN=$(token book-search book-search-dev-secret) || return 1
ADMIN_TOKEN=$(token book-search-admin book-search-admin-dev-secret) || return 1
