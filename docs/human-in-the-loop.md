# Human-in-the-Loop Approval

## Goal

Allow the agent to recommend a financial action while requiring explicit human authorization for higher-value refunds.

The principle is:

> **AI can recommend an action; a human authorizes high-risk actions.**

## Flow

```text
Customer
   |
   v
Supervisor
   |
   v
Resolution Agent
   |
   +--> RAG policy context
   +--> checkRefundPolicy()
   +--> getPayment()
   |
   +--> amount <= ₹1,000
   |       |
   |       +--> createRefund()
   |
   +--> amount > ₹1,000
           |
           v
     WAITING_FOR_HUMAN
           |
           v
     Approval Console
       /         \
   Approve       Reject
      |             |
      v             v
 createRefund     End
```

## Approval state

```text
PENDING
   |
   +--> APPROVED
   |
   +--> REJECTED
```

Approval records are persisted in PostgreSQL, so the decision does not depend on in-memory application state.

## API

```http
GET  /api/approvals/pending
POST /api/approvals/{id}/approve
POST /api/approvals/{id}/reject
```

Approval requests contain the order, amount, reason, execution ID, creation time, and final decision metadata.

## UI

The UI now contains:

- Seeded demo cases
- One-click prompts for each order
- Pending approval console
- Approve / Reject actions
- Agent execution trace
- Loading state while the agent is running

## Why the threshold exists

The threshold is intentionally simple for the portfolio demo:

```text
<= ₹1,000  -> autonomous refund
>  ₹1,000  -> human approval
```

In a production system this would normally be policy/configuration driven and could incorporate customer risk, refund history, payment risk, and role-based authorization.

## Important safety boundary

The human approval endpoint does not directly write arbitrary refund rows. Approval invokes the same controlled `createRefund` business tool used by the agent, preserving backend validation, refund limits, and idempotency.

## Observability

The lifecycle emits:

```text
HUMAN_APPROVAL_REQUESTED
HUMAN_APPROVAL_DECISION
```

alongside the existing model, tool, RAG, and agent events.

## Demo scenarios

The `human-in-the-loop` branch seeds orders covering:

- Normal delayed refund
- High-value delayed refund requiring approval
- Short delay that should not qualify automatically
- Delivered orders
- Active shipments
- Cancelled orders
- Existing completed refunds
- Different payment states
