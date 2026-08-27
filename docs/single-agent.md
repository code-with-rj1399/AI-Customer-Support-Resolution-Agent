# Single-Agent Orchestration

## Architecture

```text
Customer
   |
   v
Agent Orchestrator
   |
   +--> OpenAI
   |
   +--> Typed tools
          |
          +--> Order
          +--> Delivery
          +--> Payment
          +--> Refund policy
          +--> Refund
```

The orchestrator uses Spring AI `ChatClient` and OpenAI to interpret the request and choose tools.

## Responsibility boundary

The agent owns intent understanding, planning, tool selection, sequencing, and response generation.

The backend owns database state, validation, business rules, idempotency, and financial mutations.

## Endpoint

```http
POST /api/agent/resolve
```

Example request:

```json
{"message":"My order 1002 is five days late. I want a refund."}
```

## Why start here

A single agent is easier to reason about and is the baseline against which the multi-agent architecture can be compared.
