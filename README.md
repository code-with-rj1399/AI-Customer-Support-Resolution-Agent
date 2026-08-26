# AI Customer Support Resolution Agent

A production-oriented customer-support backend and Agentic AI orchestration platform. The system uses Gemini through Spring AI for reasoning and tool selection, while the backend remains the source of truth for customer, order, payment, delivery, refund, and ticket state.

> **Current status:** The `dev` branch contains the deterministic backend plus the first Supervisor Agent implementation. The agent is intentionally thin: Gemini reasons about the request and selects typed tools; deterministic Java services enforce business rules and execute state changes.

## Technology baseline

- Java 21
- Spring Boot 4.1.1
- Spring MVC
- Spring Data JPA / Hibernate
- PostgreSQL 17
- Flyway
- Spring Boot Actuator
- Maven
- Docker / Docker Compose
- Spring AI 2.0.1
- Google Gemini via `spring-ai-starter-model-google-genai`

## Architecture

```text
                         Customer Request
                                |
                                v
                  +---------------------------+
                  |    Supervisor Agent       |
                  |  Spring AI + Gemini       |
                  +-------------+-------------+
                                |
                         Tool selection
                                |
          +---------------------+----------------------+
          |          |           |          |          |
          v          v           v          v          v
      Customer     Order     Delivery   Payment    Refund Policy
        Tool       Tool        Tool       Tool         Tool
          |          |           |          |          |
          +----------+-----------+----------+----------+
                                |
                                v
                         Refund / Ticket Tool
                                |
                                v
                  +---------------------------+
                  | Deterministic Backend     |
                  | CustomerSupportService    |
                  +-------------+-------------+
                                |
                                v
                           PostgreSQL
```

### Responsibility boundary

**Agent layer owns:**

- Understanding the customer's request
- Reasoning and planning
- Deciding which tools are required
- Sequencing tool calls
- Producing the final customer-facing response
- Deciding when more information is required

**Backend owns:**

- Database state
- Business rules
- Validation
- Idempotency
- Refund eligibility
- Payment and delivery state
- Persisting refunds and support tickets

The LLM is **not** trusted to implement business policy. It must call deterministic backend tools to obtain facts and perform controlled actions.

## Agent implementation

The current Supervisor Agent is under:

```text
src/main/java/com/rj1399/customersupport/agent/
├── AgentController.java
├── AgentOrchestrator.java
└── CustomerSupportAgentTools.java
```

### Agent endpoint

```http
POST /api/agent/resolve
Content-Type: application/json
```

Example:

```bash
curl -X POST http://localhost:8080/api/agent/resolve \
  -H 'Content-Type: application/json' \
  -d '{
    "message": "My order 1002 is five days late. I want a refund."
  }'
```

The intended execution is:

```text
User
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
 +--> createRefund(1002)       <-- only when backend policy allows it
 |
 v
Final customer response
```

The LLM can choose and sequence tools, but it cannot bypass the service layer.

## Spring AI tool layer

`CustomerSupportAgentTools` exposes deterministic capabilities using Spring AI `@Tool` methods:

| Tool | Purpose |
|---|---|
| `getCustomer` | Retrieve customer facts |
| `getOrder` | Retrieve order state |
| `getDeliveryStatus` | Determine delivery status and delay |
| `getPayment` | Verify payment state and amount |
| `checkRefundPolicy` | Get the authoritative refund decision |
| `createRefund` | Execute a controlled, idempotent refund |
| `getSupportTicket` | Retrieve an existing support ticket |

The tools delegate to `CustomerSupportService`; the agent never accesses JPA repositories directly.

## Gemini configuration

The `dev` branch uses Google Gemini rather than OpenAI.

Create a local `.env` file:

```env
GEMINI_API_KEY=your-gemini-api-key
GEMINI_MODEL=gemini-2.5-flash
AGENT_ENABLED=true
```

`.env` is ignored by Git and must never be committed.

Then start the application:

```bash
docker compose up --build
```

The Docker Compose configuration passes the Gemini configuration into the application:

```yaml
GEMINI_API_KEY: ${GEMINI_API_KEY:-}
GEMINI_MODEL: ${GEMINI_MODEL:-gemini-2.5-flash}
AGENT_ENABLED: ${AGENT_ENABLED:-true}
```

The agent endpoint is feature-gated. If `AGENT_ENABLED=false`, `/api/agent/resolve` is not registered.

## Agentic use case

Example customer request:

> "My order 1002 is five days late. I want a refund."

The agent should investigate first rather than immediately issuing a refund.

```text
Customer message
      |
      v
Supervisor Agent
      |
      +--> Order information
      |
      +--> Delivery status
      |
      +--> Payment status
      |
      +--> Refund policy
      |
      +--> Decision
             |
             +--> Eligible + safe --> Create refund
             |
             +--> Not eligible --> Explain why
             |
             +--> High risk --> Human approval
```

For demo order `1002`, the backend data represents an order that is five days late and has a captured payment.

## Backend API / tool contracts

```text
GET  /api/customers/{customerId}
GET  /api/orders/{orderNumber}
GET  /api/orders/{orderNumber}/delivery
GET  /api/orders/{orderNumber}/payment
GET  /api/refund-policy/{orderNumber}
POST /api/refunds
GET  /api/tickets/{ticketNumber}
POST /api/tickets
```

The agent uses the typed tool layer rather than calling these HTTP endpoints itself.

## Business rules

The backend enforces:

- Refund requires at least 3 days of delivery delay.
- Payment must be `CAPTURED`.
- Automatic refunds are capped at ₹5,000.
- A completed refund cannot be created twice for the same order.
- Refund creation requires an idempotency key.
- Repeating a refund request with the same idempotency key returns the existing refund.

These rules are deterministic. Gemini may reason about the customer's request, but it cannot override these rules.

## Tool failure and resilience roadmap

The next agent-engineering iteration will distinguish retryable and non-retryable failures:

```text
Tool call
 |
 +--> 2xx ---------------------> Continue reasoning
 |
 +--> timeout / 5xx -----------> Bounded retry + backoff
 |
 +--> 429 ----------------------> Rate-limit handling
 |
 +--> 4xx validation -----------> Do not blindly retry
 |
 +--> repeated failure ---------> Graceful fallback / escalation
```

Gemini provider failures should similarly use bounded retries and timeouts. Authentication and malformed-request errors should not be retried blindly.

## Safety boundary

The agent must not translate natural-language reasoning directly into unrestricted database mutations.

For a refund:

```text
Gemini reasoning
      |
      v
Refund Policy Tool
      |
      v
Deterministic eligibility check
      |
      +---- Not eligible ------> Explain to customer
      |
      +---- Eligible ----------> Refund Tool
                                    |
                                    +--> Idempotency check
                                    +--> Business validation
                                    +--> Persist refund
```

For higher-risk actions, the planned architecture includes a human-in-the-loop approval step.

## Token and cost control roadmap

The agent should use the LLM for reasoning rather than work ordinary backend code can perform more cheaply.

Planned controls:

- Keep tool responses small and focused.
- Fetch only information required for the current decision.
- Keep system instructions concise.
- Avoid repeatedly sending the same order/payment context.
- Route deterministic operations directly to backend services.
- Track input/output tokens and model cost per agent execution.
- Add model routing for simple versus complex requests.

## Memory and RAG roadmap

Future iterations can add:

- Conversation memory
- Customer interaction history
- Refund/support policy documents
- RAG over support knowledge
- Previous-resolution retrieval

Memory will remain separate from transactional business state.

## Demo data

The Flyway seed migration creates:

- Customer `Rajesh Kumar`
- Customer `Priya Sharma`
- Order `1001` - delivered
- Order `1002` - intentionally five days late
- Order `1003` - currently shipping
- Payments for the demo orders
- Support ticket data

## Run locally

### Docker Compose

```bash
docker compose up --build
```

The API is available at:

```text
http://localhost:8080
```

Health check:

```bash
curl http://localhost:8080/actuator/health
```

### Fresh local database

If you need to recreate the demo database:

```bash
./scripts/reset-local-db.sh
```

This removes the local PostgreSQL Docker volume so Flyway can recreate the schema and seed data.

## Try the backend tools

```bash
curl http://localhost:8080/api/orders/1002
```

```bash
curl http://localhost:8080/api/orders/1002/delivery
```

```bash
curl http://localhost:8080/api/orders/1002/payment
```

```bash
curl http://localhost:8080/api/refund-policy/1002
```

Direct refund API:

```bash
curl -X POST http://localhost:8080/api/refunds \
  -H 'Content-Type: application/json' \
  -d '{
    "orderNumber": "1002",
    "reason": "Delivery delayed beyond policy threshold",
    "idempotencyKey": "agent-demo-1002-refund-1"
  }'
```

Repeat the same request with the same idempotency key to verify duplicate protection.

## Try the Agentic AI flow

1. Configure `.env` with a Gemini API key.
2. Set `AGENT_ENABLED=true`.
3. Start the application with Docker Compose.
4. Send a natural-language customer request:

```bash
curl -X POST http://localhost:8080/api/agent/resolve \
  -H 'Content-Type: application/json' \
  -d '{
    "message": "My order 1002 is five days late. I want a refund."
  }'
```

The first version intentionally keeps the orchestration simple: one Supervisor Agent with deterministic domain tools. Additional specialized agents can be introduced when there is a clear responsibility boundary rather than creating agents artificially.

## AWS EC2 deployment

The application is packaged as a standard Spring Boot container and is intentionally deployable to a small EC2 instance without Kubernetes.

```bash
mvn clean package -DskipTests
docker compose up -d --build
```

For production:

- Do not commit API keys or database passwords.
- Use EC2 environment/secret management for credentials.
- Restrict security-group ports.
- Put the application behind HTTPS/reverse proxy or an AWS load balancer.
- Use managed PostgreSQL such as Amazon RDS instead of the demo PostgreSQL container.

## Agent implementation roadmap

1. Supervisor Agent + typed tools — **implemented**
2. Gemini provider integration — **implemented**
3. Tool execution tracing — next
4. Bounded retries and timeouts — next
5. Human approval for high-risk actions
6. Conversation memory
7. RAG over support policies
8. Token/cost tracking
9. Agent evaluation dataset
10. Multi-agent specialization where justified

## Design principle

> **Backend owns truth. Agent owns reasoning.**

The backend is the system of record and enforces business invariants. Gemini is a reasoning and orchestration component. This separation is intentional and is the core architectural decision of the project.
