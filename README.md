# AI Customer Support Resolution Agent

A production-oriented customer-support backend and **Agentic AI learning platform** built with Spring AI and OpenAI.

> **The agent owns reasoning. The backend owns truth.**

The system demonstrates how an agent can understand intent, plan, delegate, retrieve knowledge, and select tools while deterministic backend code remains responsible for business rules, authorization, persistence, and financial mutations.

## Live Demo

**[Open the Agent UI](https://ai-customer-support-resolution-agent.vercel.app/)**

Use the UI to chat with the agent and compare **Single-Agent vs Multi-Agent** execution, including visible tool/knowledge execution traces.

## What This Project Covers

| Task / Capability | Summary | Detailed Guide |
|---|---|---|
| Tool Calling | Exposes typed business capabilities as Spring AI tools and keeps repository access behind backend services. | [Tool Calling](docs/tool-calling.md) |
| Single-Agent Orchestration | Uses one agent to understand requests, select tools, retrieve context, and produce a resolution. | [Single Agent](docs/single-agent.md) |
| Multi-Agent Architecture | Uses a supervisor with specialist agents for order investigation, resolution, and communication. | [Multi Agent](docs/multi-agent.md) |
| Agent Orchestrator | Defines the orchestration flow and responsibility boundaries around agent execution. | [Agent Orchestrator](docs/agent-orchestrator.md) |
| Agent Tool Contract | Defines structured contracts between agents and backend capabilities. | [Agent Tool Contract](docs/agent-tool-contract.md) |
| Agentic RAG | Retrieves relevant policy context from PostgreSQL/PGVector using OpenAI embeddings. | [RAG](docs/rag.md) |
| Human-in-the-Loop | Requires approval for high-risk refund operations before financial mutation. | [Human-in-the-Loop](docs/human-in-the-loop.md) |
| Observability / SSE | Streams high-level execution events and exposes tool/knowledge timing without exposing chain-of-thought. | [Observability](docs/observability.md) |
| Agent UI | Provides the demo chat UI and Single-Agent vs Multi-Agent architecture selector. | [UI](docs/ui.md) |
| Safety Boundary | Keeps business truth, validation, authorization, and mutations in deterministic backend code. | [Safety Boundary](docs/safety-boundary.md) |
| Guardrails | Blocks prompt-injection attempts and validates high-risk tool execution before mutations. | [Guardrails](docs/guardrails.md) |
| Agent Evaluation | Evaluates observable agent behavior such as tool selection and business outcomes using real agent execution. | [Agent Evaluation](docs/agent-evaluation.md) |

For the complete documentation index, see [`docs/README.md`](docs/README.md).

## Architecture

```text
Customer Request
       |
       v
Input Guardrail
       |
       v
Supervisor / Agent Orchestrator
       |
       +----------------------+----------------------+
       |                      |                      |
       v                      v                      v
Order Agent            Resolution Agent      Communication Agent
       |                      |
       v                      v
Order / Delivery /      RAG Knowledge Search
Payment Tools                 |
                              v
                           PGVector
                              |
                              v
                    Deterministic Backend Rules
                              |
                              v
                       Tool Guardrails / HITL
                              |
                              v
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
- Persistence
- Security and authorization guardrails

**RAG is context, not authority.** Retrieved policy content can inform the agent, but deterministic Java services decide whether a state-changing action is allowed.

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

```http
POST /api/agent/resolve
POST /api/multi-agent/resolve
```

## Tool Calling

Business capabilities are exposed as typed Spring AI tools, including customer, order, delivery, payment, knowledge, refund, and support-ticket operations.

The agent never gets direct JPA repository access.

## Agentic RAG

The current policy knowledge directory contains:

```text
src/main/resources/knowledge/
├── payment-policy.md
└── refund-policy.md
```

Policy documents are chunked, embedded with `text-embedding-3-small`, and stored in PostgreSQL/PGVector. At runtime, the resolution flow retrieves relevant policy chunks before producing a grounded response.

## Safety and Guardrails

The system uses defense in depth:

```text
Customer input
      |
      v
Prompt Injection Guardrail
      |
      v
LLM
      |
      +---- RAG / Tool results treated as untrusted data
      |
      v
Tool Execution Guardrail
      |
      v
Backend policy + authorization
      |
      +---- HITL approval when required
      |
      v
Financial mutation
```

Refund operations must go through the controlled refund workflow. Backend validation, policy checks, idempotency, and human approval cannot be bypassed by model output or user instructions.

## Live Observability

The execution trace exposes operational events without exposing hidden reasoning or chain-of-thought.

Examples include:

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

The UI can display trace events and durations while SSE streams live execution events.

## Agent Evaluation

The evaluation system measures observable agent behavior using the real Spring AI agent and captured execution traces.

Current evaluation areas include **tool selection** and **business/task outcomes**. Additional metrics such as tool sequencing, policy compliance, answer quality, retrieval quality, latency, token usage, and estimated cost are planned.

See [`docs/agent-evaluation.md`](docs/agent-evaluation.md) for the evaluation design.

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

## Run Locally

Create a local `.env` file from the example and provide your OpenAI API key:

```env
OPENAI_API_KEY=your-openai-api-key
OPEN_AI_MODEL=gpt-5-mini
OPEN_AI_EMBEDDING_MODEL=text-embedding-3-small
AGENT_ENABLED=true
RAG_ENABLED=true
RAG_TOP_K=4
RAG_SIMILARITY_THRESHOLD=0.60
```

Then start the stack:

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

## Learning Path

Start with the core mechanics and then move into production concerns:

**Tool Calling → Single Agent → Multi-Agent → RAG → Observability → Human-in-the-Loop → Guardrails → Agent Evaluation**

## Documentation

See [`docs/README.md`](docs/README.md) for the complete feature map and links to every task-specific guide.