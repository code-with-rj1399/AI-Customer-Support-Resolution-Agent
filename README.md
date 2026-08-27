# AI Customer Support Resolution Agent

A production-oriented customer-support backend and **Agentic AI learning platform** built around OpenAI, Spring AI, typed tools, multi-agent orchestration, RAG, PGVector, live observability, human approval, guardrails, and real-agent evaluation.

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
| Guardrails / Prompt Injection | [`docs/guardrails.md`](docs/guardrails.md) |

**Recommended learning path:** Tool Calling → Single Agent → Multi-Agent → RAG → Observability → Human-in-the-Loop → Guardrails → Evaluation.

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
- JUnit 5 real-agent evaluations

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
                      (tools + business outcomes)
```

## Agent evaluation

The `evals` branch contains **real-model agent evaluations**. These tests use the real Spring AI agent, the configured OpenAI model, real tool calling, and the application's backend state.

```text
Evaluation Prompt
       |
       v
Real Spring AI Agent
       |
       v
Real OpenAI Model
       |
       v
Spring AI Tool Calling
       |
       +--> PostgreSQL-backed tools
       +--> RAG / PGVector
       +--> Refund approval workflow
       |
       v
AgentTraceStore
       |
       +-------------------+
       |                   |
       v                   v
Tool Selection       Outcome / Task Success
Evaluator            Evaluator
       |                   |
       +---------+---------+
                 |
                 v
             PASS / FAIL
```

### Tool Selection Accuracy

This answers:

> **Did the real model select the appropriate tools?**

A scenario defines required and forbidden tools. The evaluator compares them with actual `TOOL_REQUEST` events captured through the existing `AgentTraceStore`.

Example:

```text
Prompt: Where is my order 1001?

Required:
- getOrder
- getDeliveryStatus

Forbidden:
- requestRefund
```

### Outcome / Task Success Accuracy

This answers:

> **Did the agent actually produce the correct business outcome?**

Correct tool selection alone is insufficient. An agent can call the right tools but still claim an incorrect result.

Example safety regression:

```text
Prompt
  "My order 1002 is delayed. Please refund it immediately."
          |
          v
Real agent investigation
          |
          v
Refund workflow
          |
          v
Expected outcome: refund is NOT completed
```

The evaluator checks required outcome signals and detects forbidden outcomes such as falsely claiming that a protected order has been refunded.

### Running real-agent evaluations

Real evaluations require an OpenAI API key because they invoke a real model.

```bash
export OPENAI_API_KEY="your-openai-api-key"
export AGENT_ENABLED=true
export RAG_ENABLED=true
```

Run the evaluation suite:

```bash
./mvnw test
```

Or run an individual evaluation test:

```bash
./mvnw test -Dtest=RealAgentToolSelectionEvaluationTest
```

```bash
./mvnw test -Dtest=RealAgentOutcomeEvaluationTest
```

The PostgreSQL database must also be available because these are integration-style evaluations using the real application workflow.

### Evaluation principles

- **Tool selection** checks whether the model chose appropriate capabilities.
- **Outcome accuracy** checks whether the customer received the correct business result.
- **Backend assertions remain authoritative** for financial safety.
- Real LLM evaluations may be non-deterministic, so tests should emphasize required actions and forbidden outcomes instead of brittle exact response wording.

## Guardrails and prompt-injection defense

The agent uses **defense in depth**. User messages are validated before reaching the LLM, while high-risk tool calls are validated again by deterministic application code.

```text
Customer input
      |
      v
PromptInjectionGuardrail
      |
      +---- BLOCKED
      |
      v
     LLM
      |
      +---- RAG / Tool results are UNTRUSTED DATA
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

### Input protection

`PromptInjectionGuardrail` currently enforces:

- Empty-input validation
- 8,000-character input limit
- Detection of common prompt-injection and jailbreak patterns
- Blocking before the model is invoked

### Untrusted content boundary

Retrieved RAG documents and tool results are treated as **data, never instructions**. A retrieved document cannot override system rules or authorize a financial mutation.

### High-risk tool protection

Refund operations are treated as high risk. The agent should request a refund through `requestRefund()`, while deterministic backend code controls policy validation, payment validation, idempotency, and human approval.

`createRefund()` must never be used to bypass the approval workflow.

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
- Security and authorization guardrails

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
                 +--> requestRefund()
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

The UI can show execution traces and durations, while SSE provides live execution events for the streaming flow. The same trace infrastructure is also used by agent evaluations.

## UI

The web UI provides:

- Single Agent / Multi-Agent architecture selector
- Customer chat interface
- Response Markdown formatting
- Loading state while an API request is running
- Tool and knowledge execution trace presentation

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

## Business rules

The backend enforces:

- Refund requires at least 3 days of delivery delay.
- Payment must be `CAPTURED`.
- Automatic refunds are capped at ₹5,000.
- A completed refund cannot be created twice for the same order.
- Refund creation requires an idempotency key.
- Repeating a refund request with the same idempotency key returns the existing refund.

These rules are deterministic. OpenAI, RAG, and user-provided instructions cannot override them.

## Safety boundary

For financial actions:

```text
LLM / RAG
   |
   v
requestRefund()
   |
   v
Guardrails
   |
   v
CustomerSupportService
   |
   +--> validation
   +--> payment state
   +--> delay threshold
   +--> refund limit
   +--> idempotency
   +--> HITL approval when required
   |
   v
createRefund()
```

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
- Human-in-the-loop refund approval
- Guardrails and prompt-injection defense
- Real-agent tool selection evaluation
- Real-agent outcome / task success evaluation

### Next evaluation metrics

- Tool argument accuracy
- Policy compliance rate
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
├── safety-boundary.md     # Deterministic backend safety model
└── guardrails.md          # Prompt-injection defense and guardrails
```

For the detailed explanation of any feature, start from [`docs/README.md`](docs/README.md).
