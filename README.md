# AI Customer Support Resolution Agent

A production-oriented customer-support backend and **Agentic AI learning platform** built with Spring AI and OpenAI.

The project demonstrates how to build an agent system where:

> **The agent owns reasoning. The backend owns truth.**

The model can understand intent, plan, delegate, retrieve knowledge, and select tools. Deterministic application code remains responsible for business rules, authorization, persistence, and financial mutations.

## What this project demonstrates

- Typed tool calling with Spring AI
- Single-agent and multi-agent orchestration
- Agent-to-agent delegation and structured task/result contracts
- Agentic RAG with OpenAI embeddings and PGVector
- Human-in-the-loop approval for high-risk refunds
- Prompt-injection guardrails and deterministic tool protection
- Live agent observability with execution traces and SSE
- Customer-support chat UI with Single Agent vs Multi-Agent comparison
- Real-model agent evaluations for tool selection and business outcomes

## Documentation

The root README focuses on the system as a whole. Detailed feature guides live under [`docs/`](docs/README.md).

| Area | Guide |
|---|---|
| Tool Calling | [`docs/tool-calling.md`](docs/tool-calling.md) |
| Single-Agent Orchestration | [`docs/single-agent.md`](docs/single-agent.md) |
| Multi-Agent Architecture | [`docs/multi-agent.md`](docs/multi-agent.md) |
| Agentic RAG / PGVector | [`docs/rag.md`](docs/rag.md) |
| Human-in-the-Loop Approval | [`docs/human-in-the-loop.md`](docs/human-in-the-loop.md) |
| Live Observability / SSE | [`docs/observability.md`](docs/observability.md) |
| Agent UI | [`docs/ui.md`](docs/ui.md) |
| Safety Boundary | [`docs/safety-boundary.md`](docs/safety-boundary.md) |
| Guardrails / Prompt Injection | [`docs/guardrails.md`](docs/guardrails.md) |
| Agent Evaluation | [`docs/agent-evaluation.md`](docs/agent-evaluation.md) |

**Suggested learning path:** Tool Calling → Single Agent → Multi-Agent → RAG → Observability → Human-in-the-Loop → Guardrails → Evaluation.

## Architecture

```text
                           Customer Request
                                  |
                                  v
                         +-------------------+
                         | Input Guardrail   |
                         | Prompt Injection  |
                         +---------+---------+
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
                     +--------------+--------------+
                                    |
                                    v
                          Deterministic Backend
                                    |
                           Tool Guardrails
                                    |
                                 PostgreSQL
                                    |
                                    v
                            Agent Evaluation
```

## Responsibility Boundary

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
- Security and authorization guardrails

RAG is context, not authority. Retrieved policy content can inform the agent, but deterministic Java services decide whether a state-changing action is actually allowed.

## Single Agent vs Multi-Agent

The UI supports both architectures so the same request can be compared directly.

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

### Endpoints

Single agent:

```http
POST /api/agent/resolve
```

Multi-agent:

```http
POST /api/multi-agent/resolve
```

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
| `requestRefund` | Request a controlled refund through policy/HITL checks |
| `createRefund` | Controlled backend refund execution |
| `getSupportTicket` | Retrieve an existing support ticket |

The agent never gets direct JPA repository access.

## Agentic RAG

The current policy knowledge directory contains:

```text
src/main/resources/knowledge/
├── payment-policy.md
└── refund-policy.md
```

At startup, documents are chunked, embedded with `text-embedding-3-small`, and stored in PostgreSQL/PGVector.

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
OpenAI Embeddings
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
              PGVector
                 |
                 v
          Relevant policy chunks
                 |
                 v
          Grounded resolution
```

## Safety and Guardrails

The system uses defense in depth:

```text
Customer input
      |
      v
PromptInjectionGuardrail
      |
      v
     LLM
      |
      +---- RAG / Tool results treated as UNTRUSTED DATA
      |
      v
ToolExecutionGuardrail
      |
      v
Backend policy + authorization
      |
      +---- HITL approval when required
      |
      v
Financial mutation
```

`PromptInjectionGuardrail` validates input before model invocation. High-risk tool calls are validated again by deterministic backend code.

Refund operations must go through `requestRefund()`. `createRefund()` cannot be used to bypass policy, idempotency, or human approval requirements.

## Live Observability

Execution tracing gives operational visibility into the model/tool workflow without exposing hidden reasoning or chain-of-thought.

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

The UI can display trace events and durations, while SSE streams live execution events. The same trace infrastructure is used by agent evaluations.

## Agent Evaluation

Agent evaluation is documented separately so the root README stays focused on architecture and usage.

The evaluation system measures observable behavior such as tool selection and business outcomes using the real Spring AI agent and captured execution traces.

Read the full guide: [`docs/agent-evaluation.md`](docs/agent-evaluation.md).

## Business Rules

The backend enforces deterministic rules including:

- Refund requires at least 3 days of delivery delay.
- Payment must be `CAPTURED`.
- Automatic refunds are capped at ₹5,000.
- A completed refund cannot be created twice for the same order.
- Refund creation requires an idempotency key.
- Repeating a refund request with the same idempotency key returns the existing refund.

OpenAI, RAG, and user-provided instructions cannot override these rules.

## Technology Baseline

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
- JUnit 5 real-agent evaluations

## OpenAI Configuration

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

## Run Locally

The local stack uses PGVector-enabled PostgreSQL:

```text
pgvector/pgvector:pg17
```

Start the application:

```bash
docker compose up --build
```

If the existing local PostgreSQL volume predates PGVector support, recreate the demo database:

```bash
docker compose down -v
docker compose up --build
```

> **Warning:** `docker compose down -v` deletes the local demo database volume. Never use it against production data.

## Example Request

```bash
curl -X POST http://localhost:8080/api/multi-agent/resolve \
  -H 'Content-Type: application/json' \
  -d '{
    "message": "My order 1002 is five days late. I want a refund."
  }'
```

## Project Roadmap

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
- Human-in-the-loop refund approval
- Guardrails and prompt-injection defense
- Real-agent tool selection evaluation
- Real-agent outcome / task success evaluation

### Next evaluation metrics

- Tool sequencing
- Policy compliance
- Answer quality / LLM-as-a-judge
- RAG retrieval quality
- Latency (P50/P95)
- Token usage and estimated cost

### Future platform work

- Agent memory
- Retry, timeout, and circuit-breaker strategies
- MCP
- Cost and latency optimization
- LangChain4j implementation for framework comparison

## Documentation Map

```text
docs/
├── README.md                 # Feature documentation index
├── tool-calling.md           # Tool calling
├── single-agent.md           # Single-agent orchestration
├── multi-agent.md            # Supervisor + specialist agents
├── rag.md                    # Agentic RAG + PGVector
├── human-in-the-loop.md      # Approval workflow
├── observability.md          # Trace events + SSE
├── ui.md                     # Demo UI and architecture selector
├── safety-boundary.md        # Deterministic backend safety model
├── guardrails.md             # Prompt-injection defense
└── agent-evaluation.md       # Agent evaluation strategy and metrics
```

Start with [`docs/README.md`](docs/README.md) for the detailed feature documentation.