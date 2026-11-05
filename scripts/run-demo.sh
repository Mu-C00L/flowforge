#!/usr/bin/env sh
set -eu

project_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
base_url=${FLOWFORGE_URL:-http://localhost:8080}

curl --fail --silent --show-error \
  -H 'Content-Type: application/json' \
  --data-binary "@$project_dir/examples/order-workflow.json" \
  "$base_url/api/v1/definitions"
printf '\n'

curl --fail --silent --show-error \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: demo-order-2026-0001' \
  --data-binary "@$project_dir/examples/start-order.json" \
  "$base_url/api/v1/workflows"
printf '\n'
