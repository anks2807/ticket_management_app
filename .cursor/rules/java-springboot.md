---
description: Java 21 / Spring Boot 4.1 coding guidelines for the backend
globs: backend/**/*.java, backend/pom.xml, backend/src/main/resources/**
alwaysApply: false
---

# Java & Spring Boot guidelines

## Platform (Spring Boot 4 generation; most AI training data is Boot 3, so these are the common traps)
- Java 21. Use records for DTOs and read models, `switch` expressions, pattern matching. No Lombok.
- `jakarta.*` only. NEVER generate `javax.*` imports.
- Jackson 3: packages are `tools.jackson.*`. NEVER generate `com.fasterxml.jackson.databind.*` imports (annotations in `com.fasterxml.jackson.annotation` remain valid).
- Testcontainers 2.x artifact and package names (e.g. `testcontainers-postgresql`). Do not use 1.x-style setup.
- Spring AI 2.0 APIs only (feature 002+). Do not use Spring AI 1.x builders/options; if unsure an API exists in 2.0.2, say so.
- Use `RestClient`, never `RestTemplate`.

## Structure
- Packages: `web` → `application` → `domain`; `application` → `persistence` → `domain`. `domain` imports no Spring classes.
- The ticket state machine lives ONLY in `TicketStatus`/`Ticket`. No transition logic in controllers, services, repositories or SQL.
- Transactions are declared on application services (`@Transactional`), never on controllers or repositories.
- Edit, transition and comment load the ticket with a pessimistic write lock (`findByIdForUpdate`). Create does not lock.
- Every ticket change inserts a `ticket_refresh_intent` row in the same transaction.

## Style
- Constructor injection only; no field injection.
- No wildcard imports. No `Optional` as a field or parameter.
- Fail fast with domain exceptions; map them to problem details in one `@RestControllerAdvice`.
- Configuration via `@ConfigurationProperties` records; no hardcoded tunables (top-K, thresholds, model names, URLs, timeouts).
- Schema changes only via Flyway migrations in `db/migration`. Never `spring.jpa.hibernate.ddl-auto` other than `validate`. Spring AI `initialize-schema` must be `false`.
- Log with SLF4J; never log full request bodies or secrets.
