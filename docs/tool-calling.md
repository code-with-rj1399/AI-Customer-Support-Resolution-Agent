# Tool Calling

## What this demonstrates

The LLM does not access PostgreSQL or JPA repositories directly. It selects typed Spring AI `@Tool` methods, while Java services execute the real business operation.

## Flow

```text
Customer request
      |
      v
     LLM
      |
      +--> getOrder()
      +--> getDeliveryStatus()
      +--> getPayment()
      +--> checkRefundPolicy()
      +--> createRefund()
      |
      v
Deterministic Java service
      |
      v
 PostgreSQL
```

## Why this matters

Tool calling separates **reasoning from execution**. The model can decide what information or action is needed, but validation, authorization, idempotency, and state mutation remain in the backend.

## Portfolio takeaway

This is the foundation for the later single-agent, multi-agent, and RAG features.
