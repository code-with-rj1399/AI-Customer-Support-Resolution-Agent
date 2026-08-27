# Agent UI

## Purpose

The web UI is a portfolio/demo surface for comparing agent architectures.

## Architecture selector

The UI lets the user switch between:

```text
Single Agent
Multi-Agent
```

The selected mode determines the backend endpoint:

```text
Single Agent -> POST /api/agent/resolve
Multi-Agent  -> POST /api/multi-agent/resolve
```

## Response presentation

The UI renders agent responses with basic Markdown formatting so bold text and lists are displayed cleanly instead of showing literal Markdown markers.

While the API is running, a loading state shows the current high-level stage. This is a user-facing progress indicator; the authoritative execution details come from the agent trace/SSE stream.

## Execution trace

Completed responses can display tool and knowledge events with durations, allowing a demo user to see how the agent reached the result without exposing hidden reasoning.
