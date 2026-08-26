#!/usr/bin/env bash
set -euo pipefail

# Local development only: removes the PostgreSQL Docker volume so Flyway
# can recreate the database schema and demo data from scratch.
echo "Resetting local PostgreSQL database..."
docker compose down -v --remove-orphans

echo "Starting PostgreSQL and the API with a fresh database..."
docker compose up --build
