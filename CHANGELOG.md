# Changelog

All notable changes are listed here. The project follows [semantic versioning](https://semver.org).

## [0.3.0] - 2026-10-22

### Added

- Architecture rules, from a map of the whole project's packages and imports: `architecture.layer-violation`,
  `architecture.package-cycle`, `architecture.domain-depends-on-framework`, `architecture.feature-internals` and
  `architecture.new-feature-dependency` (58 rules in six categories). Layer rules can be declared in
  `spring-inspector.yml`.
- Architecture styles are recognised with their evidence: layered, package by feature, hexagonal, clean / onion,
  Spring Modulith, CQRS, event-driven, multi-module builds and several applications in one repository.
- `spring-inspector architecture [--format terminal|markdown|json]` and the IntelliJ **Architecture** tab show the
  styles, a diagram of the parts and their dependencies, cycles, overloaded classes, and concrete suggestions for a
  cleaner structure.

### Changed

- Spring Inspector's own code has no package cycles any more: its architecture check found three, now fixed.

### Fixed

Found by running every rule on Spring PetClinic, PetClinic REST and BuckPal and reviewing each finding (45 findings
before, 14 after, with no false alarms left in that sample):

- `security.sql-concatenation` no longer reports queries built only from numbers, booleans or UUIDs, including
  `id.toString()` and `String.valueOf(id)`; such values cannot inject SQL.
- Clean-code rules no longer report test code (`src/test`), where long test methods and catch-alls are normal.
- `security.csrf-disabled` no longer reports a security chain that permits every request and has no login.
- `cleancode.unused-private` treats fields read by Lombok-generated code, JPA or `@ConfigurationProperties` as used.
- The architecture report lists dependencies in the same order on every run.

## [0.2.0] - 2026-10-22

### Added

- IntelliJ: findings are underlined in the editor, marked in the scroll bar and shown with gutter icons; clicking an
  icon shows the finding in the tool window.
- IntelliJ: **Alt+Enter → Apply Spring Inspector fix** applies a finding's patch after a preview, only if the code
  is unchanged since the run.
- IntelliJ: commits made from the IDE are inspected first, with Review / Commit Anyway / Cancel when the gate blocks.
- IntelliJ: **Inspect Current File**, and **Inspect with Spring Inspector** for files and folders in the project view
  and the editor's right-click menu.
- IntelliJ: plugin icon, "What's new" notes, and the commit check in Settings → Tools → Spring Inspector.
- Command line: `check --files <file>...` reviews whole files, also outside git.
- A release calendar of Java, Spring Boot and Spring AI, downloaded with the advisories from endoflife.date and Maven
  Central, keeps version rules current without a new release. `migration.spring-boot-support` now uses the
  published end-of-support dates and warns three months ahead.
- New rules: `migration.newer-patch` (a newer Spring Boot or Spring AI patch release exists) and
  `migration.java-support` (the Java version no longer gets free updates).
- IntelliJ: once a day, after a run, the plugin says when a newer version is on GitHub (can be turned off).
- A weekly GitHub workflow opens an issue when Java, Spring Boot or Spring AI ship a new release line, so the rules
  are reviewed for it.
- Vulnerability advisories stay current by themselves: the IntelliJ plugin refreshes them in the background when
  they are a day old, and the git hook runs `update --max-age 1d --quiet` before each check. A failed download waits
  an hour before the next try.
- CI checks the plugin with JetBrains' Plugin Verifier against IntelliJ IDEA 2025.1, 2025.2 and 2026.2, and releases
  can publish to the JetBrains Marketplace ([docs/releasing.md](docs/releasing.md)).

## [0.1.1] - 2026-10-22

### Security

- Spring Inspector now uses Jackson 3.1.7. Spring Boot 4.1.1 manages Jackson 3.1.5, which has five published
  advisories (three high), fixed in 3.1.6 and 3.1.7. Spring Inspector found them in its own build.

## [0.1.0] - 2026-10-22

First public release.

### Added

- Command line: `check` (staged changes, changes since a branch, or the whole project, also outside git), `init`,
  `install-hook` and `update`; terminal, JSON and SARIF output.
- 51 rules: 16 security, 14 Spring Boot, 5 Spring AI, 12 clean code and 4 migration, each version-aware.
- Vulnerable dependencies from OSV.dev, found without running the build, with the lowest fixing version.
- Per-project settings in `spring-inspector.yml` and `// inspector:ignore` with a required reason.
- IntelliJ plugin for JetBrains IDEs 2025.1+: report card tool window, finding details, open in editor.
