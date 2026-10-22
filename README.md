# Spring Inspector

[![CI](https://github.com/fereshteh-dev/spring-inspector/actions/workflows/ci.yml/badge.svg)](https://github.com/fereshteh-dev/spring-inspector/actions/workflows/ci.yml)
[![Licence](https://img.shields.io/badge/licence-Apache--2.0-blue.svg)](LICENSE)

**A private code reviewer for Java, Spring Boot and Spring AI that runs on your machine.**

Spring Inspector reviews your code for security problems, Spring Boot and Spring AI mistakes, clean-code issues,
outdated APIs and vulnerable dependencies, and gives you a report card. Every finding tells you **what is wrong**,
**why it matters** and **how to fix it**, often with a ready patch. It works inside IntelliJ IDEA, on the command
line and as a git pre-commit hook.

- **Spring-aware:** it understands `@Transactional`, Spring Security, actuator, Spring AI prompts and tools, and your
  Java, Spring Boot and Spring AI versions. Generic linters do not.
- **Private:** your code never leaves your machine. There is no server, no account and no cloud.
- **Fast:** a typical commit is checked in a few seconds; unchanged files are not inspected again.

```
Spring Inspector  score 60/100

  [FAIL] Security     2 blocker, 1 info
      BLOCKER pom.xml:21  log4j-core 2.14.1 has 7 known vulnerabilities (2 critical, 1 high, 4 moderate)
      BLOCKER src/main/java/com/acme/Orders.java:9  SQL built from runtime values is passed to executeQuery()
  [ OK ] Spring Boot  no findings
  [HINT] Clean code   1 minor
  [ OK ] Migration    no findings
  Vulnerability advisories from 2026-10-21
```

## Install

Download the latest release from
**[GitHub Releases](https://github.com/fereshteh-dev/spring-inspector/releases/latest)**:

| File | What it is |
|------|------------|
| `spring-inspector-intellij-<version>.zip` | The IntelliJ plugin |
| `spring-inspector-<version>.jar` | The command line and git hook |
| `SHA256SUMS` | Checksums to verify the downloads |

### IntelliJ IDEA plugin

Works in IntelliJ IDEA (Community and Ultimate) and the other JetBrains IDEs, version 2025.1 or newer.

1. Download `spring-inspector-intellij-<version>.zip`. Do not unzip it.
2. In IntelliJ, open **Settings → Plugins**, click **⚙ → Install Plugin from Disk…**, choose the zip and restart the
   IDE.
3. Open the **Spring Inspector** tool window at the bottom, or use **Tools → Inspect Project with Spring Inspector**.
4. Click **Inspect Whole project**, **Inspect Staged changes** or **Inspect Current File**. Select a finding to read
   the explanation and the fix; double-click it to jump to the code.
5. In the editor, findings are underlined with an icon in the gutter. Put the caret on one and press **Alt+Enter →
   Apply Spring Inspector fix** to see the change and apply it.
6. Commits made from IntelliJ are checked first; you can review the problems or commit anyway. Turn this off with
   the **Check with Spring Inspector** option in the commit window.

The inspector itself needs **Java 25 or newer**. IntelliJ IDEA 2026.2 already runs on Java 25, so there is nothing to
set up. With an older IDE, install a JDK 25 and choose it in **Settings → Tools → Spring Inspector**.

### Command line and git hook

Needs Java 25 or newer (`java -version`).

```shell
java -jar spring-inspector.jar check --all                   # review the whole project
java -jar spring-inspector.jar check                         # review only your staged changes
java -jar spring-inspector.jar check --base main             # review everything since you branched off main
java -jar spring-inspector.jar check --files src/main/java/com/acme/Orders.java   # review whole files
java -jar spring-inspector.jar check --details security      # explain every finding of one category
java -jar spring-inspector.jar install-hook                  # review every commit automatically
java -jar spring-inspector.jar update                        # download vulnerability advisories (do this weekly)
```

The [user guide](docs/user-guide.md) covers every command, CI with SARIF, and troubleshooting.

## What it checks

51 rules in five categories. See the [rule catalogue](docs/rules/README.md) for all of them.

| Category | Examples |
|----------|----------|
| **Security** (16) | SQL injection, hard-coded secrets, weak cryptography, `permitAll()` on admin paths, CSRF off, exposed actuator, XXE, SSRF, path traversal, unsafe deserialization, vulnerable dependencies |
| **Spring Boot** (14) | field injection, `@Transactional` that never starts a transaction, entities in controllers, missing `@Valid`, N+1 queries, blocking calls in reactive code, virtual-thread pinning |
| **Spring AI** (5) | user input in system prompts, one chat memory for all users, destructive tools without confirmation, model output used in SQL or shell commands |
| **Clean code** (12) | long methods, deep nesting, swallowed exceptions, returning `null`, `Optional` misuse, data classes that should be records |
| **Migration** (4) | Spring Boot lines out of support, `javax` → `jakarta`, removed Spring Security and Spring AI APIs |

### Vulnerable dependencies

Run `update` once (and then weekly) to download the public vulnerability advisories from [OSV.dev](https://osv.dev),
which include the GitHub and Spring advisories. After that, checks work offline. Spring Inspector reads your
dependencies without running your build: from `pom.xml` and Gradle files, from parents and BOMs such as Spring Boot's,
and from an application jar you have already built. Every report says how old the advisories are.

## Configure

Run `java -jar spring-inspector.jar init` to create a commented `spring-inspector.yml` in your project. In it you can
turn rules and categories off, change severities and when a commit is blocked, set limits such as the maximum method
length, and ignore generated code. To accept one finding, add a comment with a reason:

```java
// inspector:ignore security.sql-concatenation reason="column name comes from an enum"
```

## Privacy

Spring Inspector never sends your code anywhere and never runs it. The only network access is `update`, which
downloads public advisory data; nothing about your project is sent.

## Build from source

Needs JDK 25.

```shell
./mvnw verify                      # builds and tests the command line: inspector-cli/target/spring-inspector.jar
cd inspector-intellij
./gradlew buildPlugin              # builds the plugin: build/distributions/spring-inspector-intellij-<version>.zip
```

To compile the plugin against an IntelliJ you have installed instead of downloading one, add
`-PlocalIde="<path to the IDE>"`.

## Status and roadmap

Spring Inspector is young. Version 0.2 adds editor highlighting, one-click fixes and a check before commit to the
IntelliJ plugin. Next come the JetBrains Marketplace, architecture rules, and published accuracy numbers. See the
[specification](docs/SPEC.md) (section 16) and the [changelog](CHANGELOG.md).

## Documentation

- [User guide](docs/user-guide.md): installation, commands, configuration, CI, troubleshooting
- [Rule catalogue](docs/rules/README.md)
- [Specification](docs/SPEC.md) and [architecture decisions](docs/adr/README.md)
- [Contributing](CONTRIBUTING.md), [releasing](docs/releasing.md) and [security policy](SECURITY.md)

## Licence

[Apache License 2.0](LICENSE). Vulnerability data comes from OSV.dev and the GitHub Advisory Database (CC-BY 4.0) and
is downloaded by each user; it is not part of this distribution.
