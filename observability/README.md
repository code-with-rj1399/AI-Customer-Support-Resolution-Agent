# Splunk Observability

This directory contains the local OpenTelemetry Collector configuration used to export application telemetry to Splunk Observability Cloud.

## Required environment variables

```bash
export SPLUNK_ACCESS_TOKEN='<your-token>'
export SPLUNK_REALM='<your-realm>'
export SPLUNK_OTLP_ENDPOINT='https://ingest.<your-realm>.signalfx.com/v1'
```

The collector exposes OTLP on ports 4317 (gRPC) and 4318 (HTTP). The Spring Boot application sends telemetry to the collector over HTTP at `http://splunk-otel-collector:4318` when running in Docker Compose.

Never commit Splunk access tokens or other credentials to the repository.
