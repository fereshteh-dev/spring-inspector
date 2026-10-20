# Spring Inspector

Offline-first code inspector for Java, Spring Boot and Spring AI projects.

> **Status:** early development (Phase 0: project skeleton). Nothing to run yet.

Run it before every commit and get a report card covering security, Spring Boot rules,
Spring AI rules, clean code and architecture. Every finding says what's wrong, why it
matters and how to fix it, with a patch you can apply. Your code never leaves your machine.

## Building

Requires JDK 25 or newer.

```shell
./mvnw verify
```

## Documentation

- [Specification](docs/SPEC.md)
- [Architecture decision records](docs/adr/README.md)
- [Rule catalogue](docs/rules/README.md)
- [Contributing](CONTRIBUTING.md)
- [Security policy](SECURITY.md)

## Licence

[Apache License 2.0](LICENSE)
