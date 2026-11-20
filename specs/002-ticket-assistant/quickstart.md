# Quickstart: Ticket Assistant (002)

**Feature**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md) | **Contract**: [contracts/openapi.yaml](./contracts/openapi.yaml)

## Prerequisites

JDK 21, Docker, pgvector Postgres (see [001 quickstart](../001-ticket-lifecycle/quickstart.md)).

Optional Bedrock: `AWS_REGION` and `AWS_PROFILE` in `.env` (no keys in repository).

## Verify without AWS

```bash
cd backend
./mvnw verify
```

Uses Testcontainers + fake embedding + stub chat; `@Tag("llm")` tests excluded by default.

## Optional Bedrock evaluation

```bash
./mvnw verify -Dgroups=llm
```

## Manual smoke (post-implement)

1. Run app with `dev` profile.
2. `POST /api/ai/refresh/rebuild` → 202.
3. `POST /api/ai/ask` `{"question":"..."}`.
4. `GET /api/ai/refresh/status`.

## Prompt caching verification (Bedrock, optional)

Prerequisites: real AWS credentials, `assistant.bedrock.chat-model-id` configured, `assistant.bedrock.prompt-caching.enabled=true`, static system prompt sized above the [model minimum](https://docs.aws.amazon.com/bedrock/latest/userguide/prompt-caching.html) for that id.

1. Issue the same `POST /api/ai/ask` twice with identical questions (ticket context may differ slightly; the static system prefix must be identical).
2. Inspect Bedrock usage metadata on the **second** response (logging or debug): expect **cache read** input tokens &gt; 0 (and/or reduced billed input vs first call). If cache read stays 0, increase the static system prompt or confirm the model’s minimum cacheable token count — caching is optional for correctness.