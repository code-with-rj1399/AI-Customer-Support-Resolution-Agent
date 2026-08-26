# Agent Observability

## Goal

Make the model/tool workflow visible without exposing hidden reasoning or chain-of-thought.

## Trace events

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

## SSE

The agent execution can be streamed as Server-Sent Events so the UI can show progress while a model/tool loop is running.

The trace store is intentionally separate from business state. It is an operational view of an execution, not the source of truth for orders or refunds.

## Example

```text
Supervisor
  |
  +--> MODEL_REQUEST
  +--> MODEL_WAITING
  +--> TOOL_REQUEST: getOrder
  +--> TOOL_RESPONSE: getOrder
  +--> KNOWLEDGE_SEARCH
  +--> KNOWLEDGE_RESPONSE
  +--> TOOL_REQUEST: checkRefundPolicy
  +--> TOOL_RESPONSE
  +--> MODEL_RESPONSE
  +--> AGENT_COMPLETED
```

## Safety

Observability should expose useful operational facts such as tool names, components, durations, and statuses. It should not expose API keys, prompts, credentials, or private model chain-of-thought.
