# Contributing

Thank you for helping. Bug reports with a small code sample, ideas for rules and pull requests are all welcome.

A step-by-step guide to adding a rule is coming; until then, copy a rule from `inspector-rules-*` and its test,
which uses the rule test kit in `inspector-rule-testkit`.

## Before you open a pull request

```shell
./mvnw spotless:apply
./mvnw verify
```

`verify` runs formatting checks (Spotless, palantir-java-format), Error Prone with NullAway,
tests, ArchUnit architecture rules and JaCoCo coverage gates (80% on core and rule modules).

## Ground rules

- `inspector-core` depends on no framework, HTTP, LLM or UI library.
- Adapters depend on core, never the reverse.
- Every check is a plugin implementing the `Check` port; checks never call each other.
- Every package has a `package-info.java` annotated with `@NullMarked`.
