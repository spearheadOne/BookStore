#!/usr/bin/env bash

# From the repository root: source ./local-setup.sh

curl -fsS --retry 10 --retry-all-errors --max-time 5 \
  http://localhost:9200/_cluster/health | jq -e '{status, number_of_nodes}' || return 1

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

book_topic=$(sed -n 's/^BOOK_TOPIC=//p' .env) || return 1
catalog_topic=$(sed -n 's/^CATALOG_TOPIC=//p' .env) || return 1
dlt_suffix=$(sed -n 's/^DLT_PREFIX=//p' .env) || return 1
dlt_suffix=${dlt_suffix:-.DLT}

for topic in "$book_topic" "$catalog_topic" "$book_topic$dlt_suffix" "$catalog_topic$dlt_suffix"; do
  docker compose exec -T kafka /opt/kafka/bin/kafka-topics.sh \
    --bootstrap-server kafka:19092 --create --if-not-exists \
    --topic "$topic" --partitions 1 --replication-factor 1 || return 1
done
