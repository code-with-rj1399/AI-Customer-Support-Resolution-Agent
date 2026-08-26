# AI Customer Support Resolution Agent

A production-oriented customer-support backend and Agentic AI learning platform. The project demonstrates OpenAI + Spring AI, tool calling, single-agent orchestration, multi-agent delegation, RAG with PGVector, live observability, and a deterministic Java backend that remains the source of truth for business state and mutations.

> **Current branch:** `multi-agent` contains the multi-agent architecture plus policy RAG. `master` remains the stable portfolio baseline.

## Technology baseline

- Java 21
- Spring Boot 4.1.1
- Spring MVC
- Spring Data JPA / Hibernate
- PostgreSQL 17 + PGVector
- Flyway
- Spring Boot Actuator
- Maven
- Docker / Docker Compose
- Spring AI 2.0.1
- OpenAI chat + `text-embedding-3-small` embeddings

## Architecture

```text
                           Customer Request
                                  |
                                  v
                         +-------------------+
                         | Supervisor Agent  |
                         +---------+---------+
                                   |
             +---------------------+---------------------+
             |                     |                     |
             v                     v                     v
       Order Agent          Resolution Agent     Communication Agent
             |                     |                     |
       +-----+-----+               |                    OpenAI
       |     |     |               v
     Order Delivery Payment   +-----------+
     Tools  Tools   Tools     | RAG Search |
                              +-----+-----+
                                    |
                                    v
                              PGVector Store
                                    |
                     +--------------+--------------+
                     |       Policy Documents      |
                     | refund / shipping / payment |
                     | cancellation / damaged item |
                     +-----------------------------+
                                    |
                                    v
                          Deterministic Backend
                                    |
                                 PostgreSQL
```

### Responsibility boundary

**Agents own:**

- Understanding customer intent
- Planning and delegation
- Selecting tools
- Retrieving relevant knowledge
- Producing customer-facing responses

**Backend owns:**

- Database state
- Business rules
- Validation
- Idempotency
- Refund eligibility
- Payment and delivery state
- Persisting refunds and tickets

The LLM and RAG layer are **not authoritative for state-changing business decisions**. Retrieved policy context helps explain a decision; deterministic Java services decide whether a refund can actually be created.

## Single Agent vs Multi-Agent

The UI on this branch lets you choose between the two implementations.

```text
Single Agent
    |
    v
Agent Orchestrator -> Tools

Multi-Agent
    |
    v
Supervisor
    +--> Order Investigation Agent
    +--> Resolution Agent
    +--> Communication Agent
```

Single-agent endpoint:

```http
POST /api/agent/resolve
```

Multi-agent endpoint:

```http
POST /api/multi-agent/resolve
```

The same customer request can therefore be tested against both architectures.

## RAG / policy knowledge base

The policy knowledge base lives under:

```text
src/main/resources/knowledge/
├── refund-policy.md
├── shipping-policy.md
├── cancellation-policy.md
├── payment-policy.md
└── damaged-item-policy.md
```

At startup, `PolicyKnowledgeService`:

1. Loads the Markdown policy files.
2. Converts each file into a Spring AI `Document`.
3. Splits documents into embedding-sized chunks with `TokenTextSplitter`.
4. Generates OpenAI embeddings using `text-embedding-3-small`.
5. Stores the chunks in PostgreSQL/PGVector.
6. Skips re-indexing when the policy documents are already present.

At runtime:

```text
Customer request
      |
      v
Resolution Agent
      |
      +--> searchKnowledgeBase(...)
                 |
                 v
            Query embedding
                 |
                 v
             PGVector
                 |
                 v
          Relevant policy chunks
                 |
                 v
          Grounded resolution
                 |
                 +--> checkRefundPolicy()
                 |
                 +--> createRefund() if backend allows
```

The RAG search is exposed as a Spring AI tool:

```java
searchKnowledgeBase(String query)
```

This means the model/tool layer can retrieve policy context without getting direct database access.

### Important design decision

RAG does **not** replace the deterministic refund policy.

For example:

```text
RAG says:
"Orders delayed by 3+ days may qualify..."

             |
             v
checkRefundPolicy(orderNumber)
             |
             v
Deterministic backend decision
             |
       +-----+-----+
       |           |
    eligible    rejected
       |           |
       v           v
 createRefund   explain reason
```

This prevents hallucinated policy text from directly causing a financial mutation.

## RAG observability

RAG operations are included in the existing execution trace:

| Event | Meaning |
|---|---|
| `KNOWLEDGE_SEARCH` | Policy vector search started |
| `KNOWLEDGE_RESPONSE` | Relevant policy chunks were returned |
| `TOOL_REQUEST` | Backend/tool invocation started |
| `TOOL_RESPONSE` | Tool completed successfully |
| `TOOL_ERROR` | Tool failed |

A multi-agent refund request can therefore show:

```text
Supervisor Agent
      |
      +--> Order Investigation Agent
      |       +--> getOrder
      |       +--> getDeliveryStatus
      |       +--> getPayment
      |
      +--> Resolution Agent
              +--> KNOWLEDGE_SEARCH
              +--> KNOWLEDGE_RESPONSE
              +--> checkRefundPolicy
              +--> createRefund
      |
      +--> Communication Agent
              +--> OpenAI
```

## Live observability

The application exposes execution traces through SSE for the single-agent flow and maintains the same trace model for multi-agent execution.

Example event types include:

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

This provides operational visibility without exposing prompts, credentials, hidden reasoning, or chain-of-thought.

## OpenAI configuration

Create a local `.env` file:

```env
OPENAI_API_KEY=your-openai-api-key
OPEN_AI_MODEL=gpt-5-mini
OPEN_AI_EMBEDDING_MODEL=text-embedding-3-small
AGENT_ENABLED=true
RAG_ENABLED=true
RAG_TOP_K=4
RAG_SIMILARITY_THRESHOLD=0.60
```

`.env` must never be committed.

### Docker

The Compose setup uses a PGVector-enabled PostgreSQL image:

```text
pgvector/pgvector:pg17
```

Start everything with:

```bash
docker compose up --build
```

If you already have an old PostgreSQL volume from the non-PGVector image, recreate it for the demo environment:

```bash
docker compose down -v
docker compose up --build
```

**Warning:** `down -v` deletes the local demo database volume. Do not use it against production data.

## Example request

```bash
curl -X POST http://localhost:8080/api/multi-agent/resolve \
  -H 'Content-Type: application/json' \
  -d '{
    "message": "My order 1002 is five days late. I want a refund."
  }'
```

Expected high-level execution:

```text
Customer
  |
  v
Supervisor
  |
  +--> Order Agent
  |      +--> getOrder(1002)
  |      +--> getDeliveryStatus(1002)
  |      +--> getPayment(1002)
  |
  +--> Resolution Agent
         +--> RAG: refund policy
         +--> checkRefundPolicy(1002)
         +--> createRefund(1002)
  |
  +--> Communication Agent
         +--> customer response
```

## Business rules

The backend enforces:

- Refund requires at least 3 days of delivery delay.
- Payment must be `CAPTURED`.
- Automatic refunds are capped at ₹5,000.
- A completed refund cannot be created twice for the same order.
- Refund creation requires an idempotency key.
- Repeating a refund request with the same idempotency key returns the existing refund.

These rules are deterministic. OpenAI and RAG cannot override them.

## Agent tools

| Tool | Purpose |
|---|---|
| `getCustomer` | Retrieve customer facts |
| `getOrder` | Retrieve order state |
| `getDeliveryStatus` | Determine delivery status and delay |
| `getPayment` | Verify payment state and amount |
| `searchKnowledgeBase` | Retrieve relevant policy context from PGVector |
| `checkRefundPolicy` | Get the authoritative refund decision |
| `createRefund` | Execute a controlled, idempotent refund |
| `getSupportTicket` | Retrieve an existing support ticket |

The tools delegate to `CustomerSupportService`; agents never access JPA repositories directly.

## Learning roadmap

Implemented on this project:

- Tool calling
- Single-agent orchestration
- Multi-agent delegation
- Agent-to-agent task/result contracts
- PostgreSQL-backed business tools
- OpenAI integration
- RAG with embeddings + PGVector
- Policy document ingestion
- Live execution tracing
- SSE observability
- Single vs multi-agent UI comparison

Next concepts:

- Human-in-the-loop approval
- Agent memory
- Guardrails and prompt-injection defense
- Evaluation and RAG quality measurement
- Retry / timeout / circuit-breaker strategies
- MCP
- Cost and latency optimization
- Framework comparison with LangChain4j
