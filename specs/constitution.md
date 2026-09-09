# Endia Constitution

## Core Principles

### I. Spec-Driven Development
Every feature starts with a written specification. Code exists only to fulfill a spec. No feature is implemented without a corresponding spec.md, plan.md, and tasks.md. The spec is the source of truth; code follows spec, not the other way around.

### II. Clean Architecture (Layered)
Backend follows classic layered architecture: domain → application → infrastructure → interfaces. Domain has zero external dependencies. Application depends on domain only (via ports). Infrastructure implements ports. Interfaces wires everything together. Frontend follows Angular standalone component patterns with signals-first approach.

### III. LLM Abstraction First
All LLM interactions go through a provider interface (`LlmProvider`). No provider-specific logic leaks into services or controllers. Swapping providers requires only a new implementation + config change. Prompts are externalized, not hardcoded in Java strings.

### IV. Quality Gate for Generated Content
All LLM-generated quiz content passes through a quality gate before being served. Distractors must be semantically plausible, non-duplicate, and non-trivial. Failed validation triggers a re-roll (max 1 retry). This is non-negotiable for educational accuracy.

### V. MVP-First, Iterate
Ship the smallest viable loop first. Gamification, auth, and advanced features come AFTER the core learning loop works end-to-end. Do not build what the MVP does not need. YAGNI applies: if it's not in the current spec, don't build it.

## Technology Constraints

- **Backend**: Java 21, Spring Boot 3.4.x, Spring Data JPA, Maven
- **Frontend**: Angular 19+, standalone components, signals
- **Database**: MySQL 8.x (no H2/SQLite in any environment)
- **LLM**: OpenAI-compatible via RestClient, configured via env vars
- **Build**: Maven for backend, Angular CLI for frontend, monorepo layout

## Development Workflow

- Features branch from `feature/endia-mvp`
- Each spec lives in `specs/<feature-name>/`
- Weekly instructions live in `instructions/week-NN-.../`
- Commit messages reference spec/task IDs where applicable
- No `main` merges until MVP is validated end-to-end

## Governance

This constitution supersedes all other practices. Amendments require:
1. Written rationale
2. Updated constitution document
3. Migration plan for existing code

**Version**: 1.0.0 | **Ratified**: 2026-09-09 | **Last Amended**: 2026-09-09
