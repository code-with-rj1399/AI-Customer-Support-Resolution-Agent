# AI Customer Support Resolution Agent

A production-oriented customer-support backend and **Agentic AI learning platform** built around OpenAI, Spring AI, typed tools, multi-agent orchestration, RAG, PGVector, and live observability.

The central architectural principle is:

> **The agent owns reasoning. The backend owns truth.**

The LLM can understand intent, plan, delegate, retrieve knowledge, and select tools. It does **not** directly access repositories or bypass deterministic business rules.

## 📚 Feature documentation

The README gives the high-level architecture. Each major concept has a focused guide under [`docs/`](docs/README.md):

| Concept | Guide |
|---|---|
| Tool Calling | [`docs/tool-calling.md`](docs/tool-calling.md) |
| Single-Agent Orchestration | [`docs/single-agent.md`](docs/single-agent.md) |
| Multi-Agent Architecture | [`docs/multi-agent.md`](docs/multi-agent.md) |
| Agentic RAG / PGVector | [`docs/rag.md`](docs/rag.md) |
| Live Observability / SSE | [`docs/observability.md`](docs/observability.md) |
| Agent UI | [`docs/ui.md`](docs/ui.md) |
| Safety Boundary | [`docs/safety-boundary.md`](docs/safety-boundary.md) |

**Recommended learning path:** Tool Calling → Single Agent → Multi-Agent → RAG → Observability → Human-in-the-Loop → Memory → Guardrails → Evaluation.

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
- OpenAI chat models
- OpenAI `text-embedding-3-small`

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

## Responsibility boundary

### Agents own

- Customer intent understanding
- Planning and delegation
- Tool selection and sequencing
- Knowledge retrieval
- Customer-facing response generation

### Backend owns

- Database state
- Business rules and validation
- Refund eligibility
- Payment and delivery state
- Idempotency
- Financial mutations
- Persistence of refunds and support tickets

RAG is also **not authoritative** for state-changing decisions. Retrieved policy content is context; deterministic Java services decide whether an action is actually allowed.

## Single Agent vs Multi-Agent

The UI supports both architectures so the same customer request can be compared directly.

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

### Single-agent endpoint

```http
POST /api/agent/resolve
```

### Multi-agent endpoint

```http
POST /api/multi-agent/resolve
```

See the detailed architecture guide: [`docs/multi-agent.md`](docs/multi-agent.md).

## Agentic RAG

Policy knowledge is stored as Markdown files under:

```text
src/main/resources/knowledge/
├── refund-policy.md
├── shipping-policy.md
├── cancellation-policy.md
├── payment-policy.md
└── damaged-item-policy.md
```

At startup:

```text
Policy Markdown
      |
      v
Spring AI Document
      |
      v
TokenTextSplitter
      |
      v
OpenAI text-embedding-3-small
      |
      v
PostgreSQL / PGVector
```

At runtime:

```text
Resolution Agent
      |
      +--> searchKnowledgeBase(query)
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

Detailed explanation: [`docs/rag.md`](docs/rag.md).

### Important RAG safety boundary

```text
RAG context
     |
     v
checkRefundPolicy()
     |
     v
Deterministic backend decision
     |
   +---+---+
   |       |
eligible rejected
   |       |
   v       v
refund   explain
```

A retrieved document cannot directly authorize a financial mutation.

## Tool Calling

Business capabilities are exposed as typed Spring AI tools. Examples include:

| Tool | Purpose |
|---|---|
| `getCustomer` | Retrieve customer facts |
| `getOrder` | Retrieve order state |
| `getDeliveryStatus` | Determine delivery status and delay |
| `getPayment` | Verify payment state and amount |
| `searchKnowledgeBase` | Retrieve relevant policy context |
| `checkRefundPolicy` | Get authoritative refund eligibility |
| `createRefund` | Execute a controlled, idempotent refund |
| `getSupportTicket` | Retrieve an existing support ticket |

The agent never gets direct JPA repository access. See [`docs/tool-calling.md`](docs/tool-calling.md).

## Live Observability

Execution tracing provides operational visibility into the model/tool workflow without exposing hidden reasoning or chain-of-thought.

Events include:

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

The UI can show execution traces and durations, while SSE provides live execution events for the streaming flow.

See [`docs/observability.md`](docs/observability.md).

## UI

The web UI provides:

- Single Agent / Multi-Agent architecture selector
- Customer chat interface
- Response Markdown formatting
- Loading state while an API request is running
- Tool and knowledge execution trace presentation

See [`docs/ui.md`](docs/ui.md).

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

## Run locally

### Docker Compose

The local stack uses PGVector-enabled PostgreSQL:

```text
pgvector/pgvector:pg17
```

Start:

```bash
docker compose up --build
```

If an existing local PostgreSQL volume was created before PGVector was introduced, recreate the demo database:

```bash
docker compose down -v
docker compose up --build
```

> **Warning:** `docker compose down -v` deletes the local demo database volume. Never use it against production data.

## Example request

```bash
curl -X POST http://localhost:8080/api/multi-agent/resolve \
  -H 'Content-Type: application/json' \
  -d '{
    "message": "My order 1002 is five days late. I want a refund."
  }'
```

High-level execution:

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
  |      +--> RAG: refund policy
  |      +--> checkRefundPolicy(1002)
  |      +--> createRefund(1002)
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

## Safety boundary

For financial actions:

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

See [`docs/safety-boundary.md`](docs/safety-boundary.md).

## Project learning roadmap

### Implemented

- Tool calling
- Single-agent orchestration
- Multi-agent delegation
- Agent-to-agent task/result contracts
- PostgreSQL-backed business tools
- OpenAI integration
- Agentic RAG
- Embeddings + PGVector
- Policy document ingestion
- Live execution tracing
- SSE observability
- Single vs Multi-Agent UI

### Next

- Human-in-the-loop approval
- Agent memory
- Guardrails and prompt-injection defense
- Agent evaluation / RAG quality measurement
- Retry, timeout, and circuit-breaker strategies
- MCP
- Cost and latency optimization
- LangChain4j implementation for framework comparison

## Documentation map

```text
docs/
├── README.md              # Feature documentation index
├── tool-calling.md        # Tool calling
├── single-agent.md        # Single-agent orchestration
├── multi-agent.md         # Supervisor + specialist agents
├── rag.md                 # Agentic RAG + PGVector
├── observability.md       # Trace events + SSE
├── ui.md                  # Demo UI and architecture selector
└── safety-boundary.md     # Deterministic backend safety model
```

For the detailed explanation of any feature, start from [`docs/README.md`](docs/README.md).
