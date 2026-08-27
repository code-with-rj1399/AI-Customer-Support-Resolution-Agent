# Agentic RAG

## Goal

Give agents access to support policies without hard-coding every piece of knowledge into prompts or Java code.

## Pipeline

```text
Markdown policies
      |
      v
Document loader
      |
      v
TokenTextSplitter
      |
      v
OpenAI text-embedding-3-small
      |
      v
PostgreSQL + PGVector
```

At runtime:

```text
Agent
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
```

## Policy documents

Stored under `src/main/resources/knowledge/`:

- refund policy
- shipping policy
- cancellation policy
- payment policy
- damaged-item policy

## Critical safety boundary

RAG is **informational context**, not the authority for a financial mutation.

For a refund:

```text
RAG context
   |
   v
checkRefundPolicy()
   |
   v
Deterministic backend decision
   |
   +--> createRefund()
```

This protects the system from treating a hallucinated or stale retrieved passage as an authorization to mutate financial state.

## Vector database

The local stack uses `pgvector/pgvector:pg17`. Spring AI creates the vector schema when configured to initialize it.

## Configuration

```env
OPEN_AI_EMBEDDING_MODEL=text-embedding-3-small
RAG_ENABLED=true
RAG_TOP_K=4
RAG_SIMILARITY_THRESHOLD=0.60
```

## Cost

`text-embedding-3-small` is inexpensive compared with the chat model, but embeddings and chat completions are still API usage. The project intentionally keeps the policy corpus small for local learning.
