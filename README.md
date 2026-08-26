# AI Customer Support Resolution Agent

A production-oriented customer-support backend designed to become the tool layer for an Agentic AI customer-resolution system.

> **Important:** The repository currently contains the deterministic backend/tool layer. The agent layer is designed around Spring AI and an LLM provider such as Gemini. The backend remains the source of truth for customer, order, payment, refund, and ticket state.

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
- Spring AI for the planned agent orchestration layer
- Gemini API as the LLM integration target

## Architecture

The system is intentionally split into two responsibilities:

```text
                    Customer Request
                           |
                           v
                 +---------------------+
                 |   Supervisor Agent  |
                 |  Reasoning / Plan   |
                 +----------+----------+
                            |
                 Selects the required tools
                            |
          +-----------------+------------------+
          |                 |                  |
          v                 v                  v
   Order / Delivery     Payment Tool     Refund Policy Tool
       Tools                                |
          |                                  v
          +--------------------------> Refund Tool
                            |
                            v
                       Ticket Tool
                            |
                            v
                 Deterministic Backend
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
- Handling tool failures and retryable errors
- Asking for human approval for high-risk actions
- Producing the final customer-facing response

**Backend owns:**

- Database state
- Business rules
- Authorization boundaries for business operations
- Validation
- Idempotency
- Refund eligibility
- Payment and delivery state
- Persisting support tickets and refunds

The LLM should **not** be trusted to implement business policy itself. It should call deterministic backend tools to obtain facts and perform controlled actions.

## Agentic use case

Example customer request:

> "My order 1002 is five days late. I want a refund."

The agent should not immediately call the refund API. It should investigate the request first.

```text
Customer
   |
   v
Supervisor Agent
   |
   +--> Get Order 1002
   |       |
   |       +--> customer
   |       +--> amount
   |       +--> status
   |
   +--> Get Delivery Status
   |       |
   |       +--> expected delivery
   |       +--> actual delivery
   |       +--> delay
   |
   +--> Get Payment Status
   |
   +--> Check Refund Policy
   |
   +--> Decision
          |
          +--> Eligible + safe
          |       |
          |       +--> Create Refund
          |
          +--> High risk / uncertain
                  |
                  +--> Human Approval
```

For order `1002`, the demo data intentionally represents a delayed order so that this workflow can be exercised end-to-end.

## Backend tool contracts

The backend exposes deterministic capabilities that can be wrapped as Spring AI tools:

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

A useful tool mapping is:

| Agent capability | Backend API | Purpose |
|---|---|---|
| Customer lookup | `GET /api/customers/{customerId}` | Retrieve customer facts |
| Order lookup | `GET /api/orders/{orderNumber}` | Retrieve order state |
| Delivery tool | `GET /api/orders/{orderNumber}/delivery` | Determine delivery status/delay |
| Payment tool | `GET /api/orders/{orderNumber}/payment` | Verify payment state |
| Policy tool | `GET /api/refund-policy/{orderNumber}` | Determine refund eligibility |
| Refund tool | `POST /api/refunds` | Execute a controlled refund |
| Ticket tool | `GET/POST /api/tickets` | Read/create support tickets |

The agent should use these APIs as **tools**, rather than directly accessing the database.

## Tool failure and resilience

Agent orchestration must distinguish between retryable and non-retryable failures.

```text
Tool call
   |
   +--> 2xx ----------------------> Continue reasoning
   |
   +--> 5xx / transient ----------> Retry with backoff
   |
   +--> 429 ----------------------> Respect rate limit / retry
   |
   +--> 4xx validation -----------> Do not blindly retry
   |
   +--> timeout ------------------> Retry within bounded limit
   |
   +--> repeated failure ---------> Graceful fallback / human escalation
```

LLM provider calls should similarly use bounded retries, timeouts, and exponential backoff for transient failures such as `503 ServiceUnavailable`. Authentication, authorization, malformed-request, and validation errors should not be retried blindly.

## Agent safety boundary

The agent must not be allowed to turn natural-language reasoning directly into unrestricted database mutations.

For sensitive actions such as refunds:

```text
LLM reasoning
     |
     v
Refund Policy Tool
     |
     v
Deterministic eligibility check
     |
     +---- Not eligible ------> Explain to customer
     |
     +---- Eligible ----------> Refund API
                                   |
                                   +--> Idempotency check
                                   +--> Business validation
                                   +--> Persist refund
```

For high-risk actions, the architecture supports a human-in-the-loop approval step before execution.

## Token and cost control

The agent should avoid sending unnecessary context to the LLM.

Recommended approach:

- Use small, focused tool responses rather than returning complete database entities.
- Fetch only the information required for the current decision.
- Keep system instructions stable and concise.
- Use conversation summarization for long conversations.
- Avoid repeatedly sending the same order/payment information.
- Route simple deterministic operations directly to backend APIs instead of asking the LLM to reason about them.
- Track token usage and model cost per agent execution.

The goal is to make the LLM responsible for **reasoning**, not for work that ordinary backend code can perform more cheaply and reliably.

## LLM provider

The agent layer is designed to work with an LLM provider such as Gemini. The provider is responsible for generating model responses and tool-selection decisions; the backend remains responsible for executing and validating business operations.

Example high-level interaction:

```text
User message
     |
     v
Supervisor Agent
     |
     |-- LLM request --> Gemini
     |                    |
     |<-- tool choice ---|
     |
     v
Backend Tool
     |
     v
Tool result
     |
     v
Supervisor Agent
     |
     |-- LLM request --> Gemini
     |
     v
Final response
```

A transient provider error such as HTTP `503` should be handled by the resilience layer rather than causing the entire customer-resolution workflow to fail immediately.

## Business rules implemented by the backend

- Refund requires at least 3 days of delivery delay.
- Payment must be `CAPTURED`.
- Automatic refunds are capped at ₹5,000.
- A completed refund cannot be created twice for the same order.
- Refund creation requires an idempotency key.
- Repeating the same refund request with the same idempotency key returns the existing refund instead of creating a duplicate.

These rules are deliberately deterministic. The agent can ask the backend for the policy decision, but it should not invent or override the policy.

## Demo data

The Flyway seed migration creates:

- Customer `Rajesh Kumar`
- Customer `Priya Sharma`
- Order `1001` - delivered
- Order `1002` - intentionally five days late
- Order `1003` - currently shipping
- Payments for all three orders
- Ticket `TKT-1001` for the delayed order

## Run locally

### Docker Compose

```bash
docker compose up --build
```

The API will be available at `http://localhost:8080`.

Health check:

```bash
curl http://localhost:8080/actuator/health
```

If you intentionally want a fresh local database, use:

```bash
./scripts/reset-local-db.sh
```

This removes the local PostgreSQL Docker volume and allows Flyway to recreate the schema and demo data.

### Run PostgreSQL separately

Start PostgreSQL with a database named `customer_support`, then run:

```bash
./mvnw spring-boot:run
```

or:

```bash
mvn spring-boot:run
```

## Try the backend tools

Get the delayed order:

```bash
curl http://localhost:8080/api/orders/1002
```

Check delivery:

```bash
curl http://localhost:8080/api/orders/1002/delivery
```

Check payment:

```bash
curl http://localhost:8080/api/orders/1002/payment
```

Check refund eligibility:

```bash
curl http://localhost:8080/api/refund-policy/1002
```

Create the refund:

```bash
curl -X POST http://localhost:8080/api/refunds \
  -H 'Content-Type: application/json' \
  -d '{
    "orderNumber": "1002",
    "reason": "Delivery delayed beyond policy threshold",
    "idempotencyKey": "agent-demo-1002-refund-1"
  }'
```

Repeat the same request with the same idempotency key. It should not create a duplicate refund.

Create a support ticket:

```bash
curl -X POST http://localhost:8080/api/tickets \
  -H 'Content-Type: application/json' \
  -d '{
    "customerId": "11111111-1111-1111-1111-111111111111",
    "orderId": "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
    "subject": "Order delayed",
    "description": "Customer is requesting a refund because the order is five days late."
  }'
```

## Agent implementation roadmap

The next implementation steps for the agent layer are:

1. Create a Spring AI supervisor/orchestrator agent.
2. Expose the backend capabilities as typed Spring AI tools.
3. Add tool descriptions and strict input validation.
4. Add bounded retry and timeout policies for LLM and tool calls.
5. Add human approval for high-risk refund actions.
6. Add conversation memory and context management.
7. Add RAG over support/refund policies where appropriate.
8. Add token/cost tracking.
9. Add agent execution tracing and structured logs.
10. Add evaluation scenarios for common and adversarial customer requests.

## AWS EC2 deployment

The application is intentionally packaged as a normal Spring Boot container so it can run on a small EC2 instance without Kubernetes.

### Build

```bash
mvn clean package -DskipTests
```

### Run with Docker

```bash
docker compose up -d --build
```

For production, replace demo database credentials with environment variables or a proper secret-management solution. Open only the required EC2 security-group ports; normally expose `8080` only behind a reverse proxy/load balancer.

## Design principle

The backend owns **truth and business rules**.

The agent owns **reasoning, planning, tool selection, orchestration, and conversation**.

The LLM is a reasoning component, **not the system of record and not the authority for business policy**.

That boundary is intentional and is the core architectural decision of this project.
