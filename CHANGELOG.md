# Changelog

All notable changes are listed here. The project follows [semantic versioning](https://semver.org).

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
