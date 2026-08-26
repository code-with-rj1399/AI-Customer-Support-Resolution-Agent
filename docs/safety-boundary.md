# Agent Safety Boundary

## Core principle

> **The agent owns reasoning. The backend owns truth.**

The LLM can interpret intent, select tools, retrieve knowledge, and formulate a response. It cannot directly mutate database state.

## Financial action flow

```text
LLM / RAG
   |
   v
checkRefundPolicy()
   |
   v
CustomerSupportService
   |
   +--> validation
   +--> payment state
   +--> delay threshold
   +--> refund limit
   +--> idempotency
   |
   v
createRefund()
```

## Why RAG is not authoritative

Retrieved documents can be stale, incomplete, or irrelevant. They provide context, while deterministic backend services enforce the actual business rule.

## Idempotency

Refund creation requires an idempotency key and duplicate refund protection. This is especially important when an agent retries a tool call or a network timeout makes the result ambiguous.

## Future control

The next planned safety feature is human-in-the-loop approval for higher-risk actions.
