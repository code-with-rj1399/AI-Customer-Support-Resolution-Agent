# Agent Observability

## Goal

Make the model/tool workflow visible without exposing hidden reasoning or chain-of-thought. This branch adds Splunk OpenTelemetry for application metrics, distributed traces, and structured logs.

## Telemetry architecture

```text
Spring Boot application
        |
        | OTLP HTTP :4318
        v
Splunk OpenTelemetry Collector
        |
        +--> traces
        +--> metrics
        +--> logs
        |
        v
Splunk Observability Cloud
```

## Docker Compose

The application sends OTLP telemetry to `http://splunk-otel-collector:4318` inside Docker Compose. The collector exposes OTLP ports 4317 (gRPC) and 4318 (HTTP).

Set these variables before starting the stack:

```bash
export SPLUNK_ACCESS_TOKEN='<your-token>'
export SPLUNK_REALM='us0'
export SPLUNK_OTLP_ENDPOINT='https://ingest.us0.signalfx.com/v1'
```

Then run:

```bash
docker compose up --build
```

Do not commit access tokens or credentials.

## Java instrumentation

The application image starts with the Splunk Distribution of OpenTelemetry Java agent. The agent provides standard JVM, HTTP, and runtime telemetry without requiring invasive instrumentation of business code.

The image starts the application using:

```bash
java \
  -javaagent:/app/splunk-otel-javaagent.jar \
  -Dsplunk.profiler.enabled=true \
  -Dsplunk.profiler.memory.enabled=true \
  -jar /app/app.jar
```

The service is identified with:

```text
OTEL_SERVICE_NAME=spring-ai-agent
OTEL_RESOURCE_ATTRIBUTES=deployment.environment=dev
```

## Trace events

```text
AGENT_STARTED
MODEL_REQUEST
MODEL_WAITING
TOOL_REQUEST
TOOL_RESPONSE
TOOL_ERROR
KNOWLEDGE_SEARCH
KNOWLEDGE_RESPONSE
MODEL_RESPONSE
AGENT_COMPLETED
AGENT_ERROR
```

The application can stream these trace events as Server-Sent Events so the UI can show progress while a model/tool loop is running.

## Safety

Observability should expose operational facts such as tool names, components, durations, statuses, trace IDs, and error metadata. It must not expose API keys, credentials, authorization headers, private prompts, or model chain-of-thought.
