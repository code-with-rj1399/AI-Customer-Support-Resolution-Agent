# AI Customer Support Resolution Agent

A production-oriented customer-support backend designed to become the tool layer for an Agentic AI customer-resolution system.

> **Important:** This repository intentionally contains **no AI agents**. The backend exposes deterministic business capabilities that you will later orchestrate with Spring AI agents.

## Technology baseline

- Java 21
- Spring Boot 4.1.1 (latest stable at project creation)
- Spring MVC
- Spring Data JPA / Hibernate
- PostgreSQL 17
- Flyway
- Spring Boot Actuator
- Maven
- Docker / Docker Compose

Spring Boot 4.1.1 is the current stable release; 4.2.0-M1 is preview and is deliberately not used.

## What this backend supports

The target agentic use case is:

> Customer: "My order 1002 is five days late. I want a refund."

The future agent layer can investigate the request by calling these backend capabilities:

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

### Example agent workflow

```text
Customer request
      |
      v
Support / Supervisor Agent
      |
      +----> Order lookup
      |
      +----> Delivery status
      |
      +----> Payment status
      |
      +----> Refund policy
      |
      v
Decision
      |
      +----> Safe action -> Refund API
      |
      +----> High-risk action -> Human approval in agent layer
```

## Business rules implemented by the backend

- Refund requires at least 3 days of delivery delay.
- Payment must be `CAPTURED`.
- Automatic refunds are capped at ₹5,000.
- A completed refund cannot be created twice for the same order.
- Refund creation requires an idempotency key.
- Repeating the same refund request with the same idempotency key returns the existing refund instead of creating a duplicate.

These rules are deliberately deterministic. The LLM should **not** be trusted to implement business policy itself; the agent should call the policy and action APIs.

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

### Option 1: Docker Compose

```bash
docker compose up --build
```

The API will be available at `http://localhost:8080`.

Health check:

```bash
curl http://localhost:8080/actuator/health
```

### Option 2: Run PostgreSQL separately

Start PostgreSQL with a database named `customer_support`, then run:

```bash
./mvnw spring-boot:run
```

or, if Maven is installed:

```bash
mvn spring-boot:run
```

## Try the agent-facing APIs

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

Repeat the same request and the same idempotency key will not create another refund.

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

For production, replace the demo database credentials with environment variables or a proper secret-management solution. Open only the required EC2 security-group ports; normally expose `8080` only behind a reverse proxy/load balancer.

## Planned Agentic AI layer

The next repository phase can add a separate agent application using Spring AI:

```text
Customer
   |
   v
Supervisor Agent
   |
   +--> Order Tool
   +--> Delivery Tool
   +--> Payment Tool
   +--> Refund Policy Tool
   +--> Refund Tool
   +--> Ticket Tool
```

Recommended future capabilities:

- supervisor / orchestration agent
- order agent
- policy agent
- refund agent
- human-in-the-loop for high-risk refunds
- tool authorization
- retry and timeout policies
- conversation memory
- RAG over support policies
- token and cost tracking
- agent execution tracing
- evaluation scenarios

## Design principle

The backend owns **truth and business rules**. Agents own **reasoning, planning, tool selection, and conversation**.

That boundary is intentional and is the core architectural decision of this project.
