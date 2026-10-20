# ADR-0001: JavaParser with symbol solver for the semantic model

- **Status:** Accepted
- **Date:** 2026-10-21
- **Deciders:** project maintainers

## Context

`inspector-java-model` turns the inspected project's sources into a model that checks query
(SPEC §4). SPEC asks for either the OpenRewrite LST or JavaParser with its symbol solver.

What matters to us:

1. **Runs on every JDK we support**: Java 25 LTS and the latest feature release, which CI tests.
   Users run the tool on whatever JDK they have.
2. **Parses Java 21–25 sources** (SPEC §2), including records, sealed types, pattern matching
   and unnamed variables.
3. **Fast cold start**: under 5 seconds for a typical commit (SPEC §6), on a pre-commit hook where
   the JVM starts fresh each time.
4. **Never executes inspected code** and never needs its build to run.
5. **Permissive licences** for an Apache-2.0 project.
6. **Can produce patches** for the "How to fix it" part of a finding.

## Evidence

We measured both candidates on 2026-10-21. A throwaway spike parsed a Java 25 file containing a
sealed interface, records, a record-pattern `switch`, string-concatenated SQL and an unnamed
variable, with no project classpath, on Temurin 25 and Temurin 27.

| | JavaParser 3.28.2 + symbol solver | OpenRewrite 8.90.4 (`rewrite-java-25`) |
|---|---|---|
| Parses on JDK 25 | Yes, cold 317 ms, warm ≈ 3 ms/file | Yes, cold 2.2 s, lossless round trip |
| Parses on JDK 27 | Yes, same timings | **No:** `Unable to construct Java25Parser. java.version: 27` |
| Java language levels | Up to `JAVA_26` | Tied to the running JDK's `javac` |
| Type resolution | String concatenation resolved to `java.lang.String`; **record-pattern `var` binding not resolved** | Full `javac` attribution |
| Format-preserving edits | `LexicalPreservingPrinter` | Built in, with diff generation |
| Runtime dependencies | ≈ 2 MB plus javassist and Guava | ≈ 28 MB including Jackson 2 databind and Guava |
| Licence | Apache-2.0 or LGPL-3.0 (dual) | Apache-2.0 for the parser; the Spring migration recipes (`rewrite-spring`) are under the Moderne Source Available License |

The JDK 27 failure is structural, not a passing bug. OpenRewrite's parser drives the running
JDK's internal `javac` API and picks an implementation by `java.version`, so each new JDK needs a
new `rewrite-java-NN` module. palantir-java-format has the same coupling and broke on JDK 27 in
our own CI (palantir/palantir-java-format#1785).

## Decision

Use **JavaParser with the symbol solver** in `inspector-java-model`, under its Apache-2.0 option.

## Consequences

**Positive**

- The tool keeps working on new JDKs without waiting for an upstream module per release.
- The pre-commit path is about 2 seconds faster before any check runs.
- A small dependency graph keeps the jar and SBOM small. It avoids a second, Jackson 2 JSON stack
  next to our Jackson 3.

**Negative, and how we handle it**

- **Type resolution is best effort.** The symbol solver misses some modern constructs (record
  pattern bindings in our spike) and resolves library types only when it is given their jars.
  `inspector-java-model` owns the type solvers: JDK reflection, the project's sources, and
  dependency jars already present in the local Maven/Gradle cache, read but never run. Resolution
  failures must not fail a check. A check falls back to syntactic matching and lowers the
  finding's confidence.
- **Patches are ours to build.** We produce them with `LexicalPreservingPrinter` (or targeted
  line edits) plus a unified-diff generator, instead of OpenRewrite's built-in diffs. The rule
  phase (Phase 3) adds the diff library and records its licence.
- **We cannot reuse OpenRewrite recipes** for migration hints. That ecosystem's Spring recipes are
  not OSI-licensed anyway. Migration hints (Phase 5) are written as our own checks.
- **Rules see JavaParser types.** Rule modules use the JavaParser AST through
  `inspector-java-model`, so a later parser change would touch rule code as well. `inspector-core`
  stays parser-free; ArchUnit enforces that core depends only on the JDK and JSpecify.

## Revisit when

- OpenRewrite's parser stops depending on the running JDK's `javac` internals, or
- symbol-solver gaps cause measurable false negatives in the evaluation harness (SPEC §14).
