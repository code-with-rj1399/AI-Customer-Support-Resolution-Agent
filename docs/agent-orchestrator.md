# Agentic AI Orchestrator

The `agent` package contains the first Agentic AI layer for the customer-support platform.

## Architecture

```text
Customer
   |
   v
POST /api/agent/resolve
   |
   v
Supervisor / Orchestrator
   |
   +--> Order Tool
   +--> Delivery Tool
   +--> Payment Tool
   +--> Refund Policy Tool
   +--> Refund Tool
   +--> Customer Tool
   +--> Support Ticket Tool
   |
   v
Final customer response
```

The orchestrator is intentionally a **supervisor agent**, not a collection of hard-coded agent-to-agent calls. The LLM decides which deterministic tools are needed for the current request and Spring AI 2.x drives the tool-calling loop.

Spring AI 2.0 made tool calling a first-class `ToolCallingAdvisor` concern in the `ChatClient` advisor chain. The model requests a tool, the application executes it, the result is returned to the model, and the loop continues until the model can answer. This is the foundation used here.

## Separation of responsibilities

### Agent layer

Responsible for:

- understanding customer intent
- planning the investigation
- selecting tools
- combining tool results
- producing the customer-facing response

### Backend domain layer

Responsible for:

- order state
- payment state
- refund policy
- refund limits
- duplicate protection
- idempotency
- transactional writes

The LLM is **not** trusted to implement business rules.

## Enable the agent

The agent is disabled by default so the base customer-support backend can run without an LLM API key.

```bash
export OPENAI_API_KEY="your-key"
export AGENT_ENABLED=true
export OPENAI_MODEL=gpt-5-mini
```

Then run:

```bash
docker compose up --build
```

## Test the orchestrator

```bash
curl -X POST http://localhost:8080/api/agent/resolve \
  -H 'Content-Type: application/json' \
  -d '{
    "message": "My order 1002 is five days late. I want a refund."
  }'
```

The expected investigation path is conceptually:

```text
Customer request
      |
      v
Supervisor Agent
      |
      +--> getOrder(1002)
      |
      +--> getDeliveryStatus(1002)
      |
      +--> getPayment(1002)
      |
      +--> checkRefundPolicy(1002)
      |
      +--> createRefund(...)
      |
      v
Customer response
```

The exact tool sequence is selected by the model. The system prompt requires the agent to investigate refund eligibility before attempting the refund.

## Important safety boundary

The refund tool does not trust the model's decision. It delegates to `CustomerSupportService`, where the deterministic rules are enforced:

- minimum delivery delay
- captured payment
- automatic refund limit
- existing completed refund check
- idempotency key

If the backend returns `HUMAN_APPROVAL_REQUIRED`, the agent must not bypass that response.

## Next evolution

The next steps should be implemented incrementally:

1. Add explicit agent execution events.
2. Add tool-call audit records.
3. Add human approval workflow for high-risk refunds.
4. Add conversation memory.
5. Add policy RAG.
6. Add retry and timeout controls around external tools.
7. Add token/cost metrics.
8. Add evaluation scenarios for refund, delivery, payment and ticket cases.
9. Split specialist agents only when there is a real domain reason to do so.
