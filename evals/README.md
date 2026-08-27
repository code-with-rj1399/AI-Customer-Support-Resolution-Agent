# Agent Evaluation (Evals)

This directory documents the production-grade evaluation strategy for the AI Customer Support Resolution Agent.

The goal of evaluation is not to ask whether the model produced a response that "looks good". The goal is to measure whether the agent completed the correct task, selected the correct capabilities, followed deterministic policies, remained grounded in backend facts, and did so within acceptable operational limits.

> **Agents own reasoning. Evaluations measure observable outcomes.**

## Why evaluation is needed

Traditional application tests can assert deterministic outputs:

```text
input -> expected output
```

An agentic system is different:

```text
input
  -> model reasoning and planning
  -> agent delegation
  -> tool calls
  -> backend state changes
  -> final response
```

The exact wording and even some execution paths can vary. Therefore, production evaluation combines deterministic assertions, trace analysis, policy checks, and, where necessary, model-based judging.

## Evaluation architecture

```text
Evaluation Dataset
        |
        v
Evaluation Runner
        |
        +--> Single Agent execution
        |
        +--> Multi-Agent execution
        |
        v
Observable Execution Trace
        |
        +--> Agent handoffs
        +--> Tool calls
        +--> Tool outputs
        +--> Final response
        +--> Latency
        +--> Usage metadata
        |
        v
Metric Evaluators
        |
        +--> Tool Selection Accuracy
        +--> Tool Sequencing
        +--> Task Success
        +--> Policy Compliance
        +--> Groundedness / Hallucination
        +--> Latency
        +--> Token and Cost Usage
        |
        v
Evaluation Report
        |
        +--> Per-test result
        +--> Per-metric score
        +--> Regression comparison
        +--> Single vs Multi-Agent comparison
```

## Production principles

The evaluation system should follow these rules:

1. **Separate production traffic from evaluation traffic.**
2. **Use deterministic fixtures for repeatable scenarios.**
3. **Never execute irreversible financial mutations against production data during automated evaluation.**
4. **Capture observable traces, not hidden chain-of-thought.**
5. **Version datasets, prompts, models, and evaluation rules.**
6. **Run the same evaluation suite when prompts, models, tools, policies, or orchestration change.**
7. **Track regressions, not only absolute scores.**
8. **Use deterministic metrics first; use LLM-as-a-Judge only where deterministic checking is insufficient.**

---

# Metric 1: Tool Selection Accuracy

## Question

Did the agent call the capabilities required to complete the task?

Example scenario:

```text
User: My order 1002 is five days late. I want a refund.
```

Expected capabilities may include:

```text
getOrder
getDeliveryStatus
getPayment
checkRefundPolicy
requestRefund
```

Observed execution:

```text
getOrder
getDeliveryStatus
getPayment
checkRefundPolicy
requestRefund
```

This scenario has full tool-selection coverage.

## Why simple equality is not enough

Production evaluation should distinguish between:

- **Required tools**: missing one means the task may be unsafe or incomplete.
- **Optional tools**: useful but not mandatory for every valid path.
- **Forbidden tools**: tools that must never be used for a scenario.
- **Unexpected tools**: unnecessary calls that increase cost, latency, or risk.

Initial metrics:

```text
Required Tool Recall = required tools called / required tools expected
Unexpected Tool Count = tools called that were not expected or optional
Forbidden Tool Violations = count of forbidden tool calls
```

A production gate can later require:

```text
Required Tool Recall = 100%
Forbidden Tool Violations = 0
```

## Status

**First metric to implement.**

---

# Metric 2: Tool Sequencing

## Question

Were tools called in a safe and logically valid order?

For a refund request:

```text
Investigate order
      -> verify delivery/payment state
      -> check policy
      -> request refund
```

A sequence such as requesting a refund before validating the order can be flagged as invalid.

## Production approach

Do not overfit to one exact sequence. Model the required ordering as precedence constraints:

```text
getOrder BEFORE requestRefund
checkRefundPolicy BEFORE requestRefund
```

This allows legitimate execution variation while enforcing safety-critical ordering.

## Status

Planned after Tool Selection Accuracy.

---

# Metric 3: Task Success

## Question

Did the system achieve the expected business outcome?

Examples:

```text
Order status request -> correct order information returned
Eligible low-risk refund -> refund completed
High-value refund -> pending human approval
Ineligible refund -> no refund created and customer informed
Approval status request -> correct approval state returned
```

## Production approach

Prefer assertions against observable backend state and structured results rather than judging response wording alone.

## Status

Planned.

---

# Metric 4: Policy Compliance

## Question

Did the agent and orchestration respect mandatory business and safety rules?

Examples:

- Payment must be `CAPTURED` before a refund.
- Delivery delay policy must be satisfied.
- A refund must not bypass human approval.
- A duplicate completed refund must not be created.
- The agent must not use forbidden mutation paths.

## Production approach

Policy compliance should be a deterministic pass/fail gate wherever possible.

```text
Violation detected -> FAIL
No violation -> PASS
```

## Status

Planned.

---

# Metric 5: Groundedness / Hallucination

## Question

Did the final response claim facts that are unsupported by tools, backend state, or approved knowledge?

Example:

```text
Tool result: paymentStatus = FAILED
Agent response: payment was successfully captured
```

This is an unsupported claim.

## Production approach

Use multiple layers:

1. Deterministic assertions for critical facts.
2. Expected-fact checks for known scenarios.
3. LLM-as-a-Judge only for nuanced semantic evaluation.

LLM-as-a-Judge results should be treated as probabilistic signals and periodically calibrated against human review.

## Status

Planned.

---

# Metric 6: Latency

## Question

How long does an execution take?

Capture:

```text
Total execution time
Supervisor time
Specialist-agent time
Tool time
Model wait time
```

## Production approach

Report percentiles rather than only averages:

```text
p50
p95
p99
```

This exposes slow-tail behavior that averages can hide.

## Status

Planned.

---

# Metric 7: Token Usage and Cost

## Question

How expensive is an execution?

Capture provider metadata when available:

```text
Input tokens
Output tokens
Total tokens
Model
Estimated cost
```

For production comparison, aggregate by scenario and architecture:

```text
Single Agent vs Multi-Agent
```

## Production approach

Cost must be treated as an operational metric, but never as a reason to relax policy or correctness checks.

## Status

Planned.

---

# Evaluation dataset design

Each scenario should be versioned and contain explicit expectations.

Conceptual example:

```json
{
  "id": "refund-late-order-human-approval",
  "input": "My order 1002 is five days late. I want a refund.",
  "architecture": "MULTI_AGENT",
  "requiredTools": [
    "getOrder",
    "getDeliveryStatus",
    "getPayment",
    "checkRefundPolicy",
    "requestRefund"
  ],
  "optionalTools": [],
  "forbiddenTools": ["createRefund"],
  "expectedOutcome": "PENDING_HUMAN_APPROVAL",
  "policyRules": [
    "PAYMENT_CAPTURED_REQUIRED",
    "HUMAN_APPROVAL_REQUIRED"
  ]
}
```

Datasets should eventually include normal, edge, adversarial, and regression scenarios.

## Scenario categories

```text
Happy path
Boundary values
Missing identifiers
Invalid orders
Ineligible refunds
Duplicate requests
Human approval pending
Human approval approved/rejected
Tool failures
Prompt injection attempts
Regression cases from real defects
```

# Execution and regression strategy

A production evaluation run should persist:

```text
Evaluation run ID
Timestamp
Git commit
Prompt version
Model version
Dataset version
Per-test metrics
Aggregate metrics
Failures
```

This allows comparison:

```text
Previous commit -> Current commit
Old prompt -> New prompt
Model A -> Model B
Single Agent -> Multi-Agent
```

# Implementation order

We will implement one metric at a time:

1. **Tool Selection Accuracy** ← current step
2. Tool Sequencing
3. Task Success
4. Policy Compliance
5. Groundedness / Hallucination
6. Latency
7. Token Usage and Cost

After each metric we will:

1. Understand the metric.
2. Add the minimal production-quality data model.
3. Implement the evaluator.
4. Add deterministic tests.
5. Run example scenarios.
6. Review limitations.
7. Update this documentation with the actual implementation.

## Definition of done

The evaluation system is production-ready when it provides repeatable, versioned, observable results and can detect meaningful regressions before deployment.

It should answer:

> Did the agent still work correctly, safely, and efficiently after this change?
