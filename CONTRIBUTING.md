# Contributing to SIF Unity Broker

Thank you for your interest in contributing to the SIF Unity Broker. This document explains how to get involved.

## Ways to Contribute

- **Bug reports** — Open an issue describing the problem, steps to reproduce, and expected behavior.
- **Bug fixes** — Submit a pull request with a fix and relevant tests.
- **Documentation** — Improve README, API docs, or inline code comments.
- **New features** — Submit a pull request. For architectural changes, open a Discussion first (see below).
- **Test coverage** — Add unit tests, property-based tests, or Bruno API tests.

## Before You Start

### Feature contributions

Bug fixes, documentation, new endpoints, and UI improvements can be submitted directly as pull requests.

### Architectural changes

Changes that affect the system's core architecture require discussion before implementation. See [GOVERNANCE.md](GOVERNANCE.md) for details. Examples:

- Adding or removing microservices
- Changing authentication/authorization models
- Modifying the message processing pipeline
- Database schema changes affecting multiple services

Open a GitHub Discussion tagged `architecture` before writing code for these.

## Development Setup

### Prerequisites

- Java 17 (Eclipse Temurin recommended)
- Maven 3.9+
- Docker (for integration tests via Testcontainers)
- PostgreSQL 14+ (or use Docker)
- Redis 6+ (or use Docker)

### Build

```bash
./mvn clean install
```

### Run Tests

```bash
# Unit + property tests (no Docker required)
./mvn test

# Integration tests (requires Docker for Testcontainers)
./mvn verify
```

### Local Development

See `BUILD-COMMANDS.md` for detailed build and run instructions.

## Pull Request Process

1. **Fork** the repository and create a branch from `main`.
2. **Write tests** for your changes — unit tests, property-based tests (jqwik), or Bruno API tests as appropriate.
3. **Ensure the build passes** — `./mvn clean install` must succeed with no test failures.
4. **Follow existing patterns** — match the code style, package structure, and conventions of the module you're modifying.
5. **Keep PRs focused** — one logical change per PR. Split large changes into smaller, reviewable pieces.
6. **Write a clear description** — explain what the change does, why it's needed, and what was tested.

## Code Standards

- **Java 17** — use modern features (records, pattern matching, text blocks)
- **Google Java Style** (adapted) — 4-space indent, 120-char line length
- **Property-based tests** (jqwik) for correctness properties
- **No shortcuts** — fix the root cause, not the symptom
- **SIF spec compliance** — check `external_docs/` when implementing protocol behavior

## Licensing

By submitting a pull request, you agree that your contribution is licensed under the [MIT License](LICENSE) that covers this project. 

To maintain a clean legal trail for public infrastructure, all contributors must sign off on their commits using the standard Developer Certificate of Origin (DCO). Please use the `-s` flag when committing: `git commit -s -m "Your commit message"`.

## Code of Conduct

Be respectful, constructive, and professional. We're building educational infrastructure — treat fellow contributors the way you'd want your students treated.

## Questions?

- Open a GitHub Discussion for general questions
- Open an Issue for bugs or feature requests
- See [GOVERNANCE.md](GOVERNANCE.md) for project roles and decision-making
