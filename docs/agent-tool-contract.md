# Agent Tool Contract

This document defines the deterministic tools that an Agentic AI layer can expose through Spring AI.

## Read tools

### getCustomer
`GET /api/customers/{customerId}`

Use when the agent needs customer identity or contact information.

### getOrder
`GET /api/orders/{orderNumber}`

Use when the agent needs order status, amount, customer, and delivery dates.

### getDeliveryStatus
`GET /api/orders/{orderNumber}/delivery`

Use when the agent needs delivery state and delay duration.

### getPayment
`GET /api/orders/{orderNumber}/payment`

Use when the agent needs payment state before discussing refunds.

### checkRefundPolicy
`GET /api/refund-policy/{orderNumber}`

Use before attempting a refund. The backend evaluates the actual business rules.

## Write tools

### createRefund
`POST /api/refunds`

Request:

```json
{
  "orderNumber": "1002",
  "reason": "Delivery delayed beyond policy threshold",
  "idempotencyKey": "unique-agent-operation-id"
}
```

The tool is deterministic and idempotent. The same idempotency key returns the existing refund.

### createSupportTicket
`POST /api/tickets`

Request:

```json
{
  "customerId": "11111111-1111-1111-1111-111111111111",
  "orderId": "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
  "subject": "Order delayed",
  "description": "Customer needs help with a delayed order."
}
```

## Recommended orchestration

For a refund request, the agent should normally execute:

```text
getOrder
   -> getDeliveryStatus
   -> getPayment
   -> checkRefundPolicy
   -> createRefund
```

The agent should not infer eligibility from conversation text when the policy API can make the decision.

## Recommended safety boundary

```text
LLM reasoning
    |
    v
Tool selection
    |
    v
Backend business rule validation
    |
    v
Database transaction
```

The LLM is not the source of truth for prices, payment state, refund eligibility, or idempotency.
