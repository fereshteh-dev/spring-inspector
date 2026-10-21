# ADR-0004: IntelliJ plugin that runs the command line in its own process

- **Status:** Accepted
- **Date:** 2026-10-22
- **Deciders:** project maintainers

## Context

The maintainers chose an IDE plugin over the local web report in SPEC §3 and §11: Spring Inspector should install as
a plugin in IntelliJ IDEA and other JetBrains IDEs and show its results there.

Three facts shape how:

1. **The IDE decides the plugin's Java version.** Plugin code runs on the IDE's bundled runtime: Java 21 in the
   2025.x IDEs, Java 25 in IntelliJ IDEA 2026.2. The inspector targets Java 25.
2. **JetBrains plugins are built with Gradle.** The IntelliJ Platform Gradle Plugin (Apache-2.0) supplies the platform
   API, packages the plugin and checks its structure. There is no supported Maven equivalent.
3. **The inspector parses whole projects.** Running it inside the IDE process would share the IDE's memory and could
   slow it down or, through a bug, bring it down.

## Decision

- **Out of process.** The plugin bundles the command line's `spring-inspector.jar` (in `cli/`) and runs
  `check --format json` on the project in a separate process, then reads the JSON report. The process runs on the
  Java chosen in *Settings | Tools | Spring Inspector*, or on the IDE's own Java when it is 25 or newer. The results
  are exactly what the command line reports, and the IDE stays responsive and safe.
- **Java 21 bytecode for the plugin only.** `inspector-intellij` compiles with `--release 21` so it loads in the
  2025.x IDEs (`since-build` 251). Everything else stays on Java 25.
- **Platform APIs only.** The plugin depends on `com.intellij.modules.platform`, not on the Java plugin, so it installs
  in every JetBrains IDE. It uses long-stable APIs (tool windows, background tasks, process handlers, settings).
- **Gradle for this module only.** `inspector-intellij` is a separate Gradle build (wrapper pinned to Gradle 9.8.1 by
  checksum) with the same Error Prone and NullAway checks. The Maven build does not include it; it packages the jar
  that the plugin bundles. CI builds both.
- **Minimal libraries.** The report is read with Jackson 3's streaming parser (`jackson-core`, Apache-2.0) only, which
  cannot clash with the Jackson 2 the IDE ships.

## Consequences

- Each run starts a JVM (about a second), which is fine for an on-demand report card. Editor highlighting as you type
  would need a long-running process and is left for later.
- The JSON report is now an interface between the command line and the plugin; changing it needs both sides.
- In the 2025.x IDEs users must point the plugin at a Java 25 installation once.
- Compatibility with 2025.1 is declared but checked only by using stable APIs; a Plugin Verifier run against those
  IDEs should be added before publishing to the JetBrains Marketplace.
