# Multi-Agent Architecture

## Goal

Split responsibilities when a single agent becomes too broad. The current workflow uses a supervisor and specialist agents.

```text
Customer
   |
   v
Supervisor
   +--> Order Investigation Agent
   |      +--> getOrder
   |      +--> getDeliveryStatus
   |      +--> getPayment
   |
   +--> Resolution Agent
   |      +--> searchKnowledgeBase
   |      +--> checkRefundPolicy
   |      +--> createRefund
   |
   +--> Communication Agent
          +--> customer response
```

## Agent contracts

Agents exchange typed `AgentTask` and `AgentResult` objects rather than sharing database access.

## Why multiple agents

Specialization creates clearer responsibility boundaries and makes delegation, observability, and future approval policies easier to implement. More agents are not automatically better; each agent should have a meaningful responsibility boundary.

## Endpoint

```http
POST /api/multi-agent/resolve
```

## Portfolio takeaway

The project can run the same customer problem through both single-agent and multi-agent architectures, making the trade-offs demonstrable rather than theoretical.
