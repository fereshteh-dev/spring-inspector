# Spring Inspector

[![CI](https://github.com/fereshteh-dev/spring-inspector/actions/workflows/ci.yml/badge.svg)](https://github.com/fereshteh-dev/spring-inspector/actions/workflows/ci.yml)
[![Release](https://img.shields.io/github/v/release/fereshteh-dev/spring-inspector)](https://github.com/fereshteh-dev/spring-inspector/releases/latest)
[![Java 25](https://img.shields.io/badge/Java-25-orange.svg)](https://adoptium.net/temurin/releases/?version=25)
[![Licence](https://img.shields.io/badge/licence-Apache--2.0-blue.svg)](LICENSE)

**A code reviewer for Java, Spring Boot and Spring AI that runs on your machine, inside IntelliJ IDEA and before every
commit.**

Spring Inspector finds the mistakes a senior developer would catch in a code review: security holes, Spring Boot and
Spring AI pitfalls, clean-code problems, architecture drift, outdated versions and vulnerable dependencies. Every
finding says **what is wrong**, **why it matters** and **how to fix it**, and many come with a one-click fix.

- **Java, Spring Boot and Spring AI:** plain Java projects get the security, clean-code, architecture and dependency
  rules; the Spring Boot and Spring AI rules apply as soon as your project uses them.
- **Knows your versions:** it reads your Java, Spring Boot and Spring AI versions and only suggests what they support.
- **Private:** your code never leaves your machine. No server, no account, no cloud.
- **Fast:** a commit is checked in seconds; unchanged files are not inspected again.
- **Free and open source** under the Apache 2.0 licence.

## What it catches

Bugs that compile, pass the tests and still reach production. For example:

```java
@Service
class Checkout {

    public void placeOrder(Cart cart) {
        this.saveOrder(cart);          // ← Spring Inspector: no transaction starts here
    }

    @Transactional
    public void saveOrder(Cart cart) { ... }
}
```

> **What is wrong:** placeOrder() calls @Transactional saveOrder() on the same object, so no transaction starts.
>
> **Why it matters:** @Transactional works through a proxy around the bean. A call from inside the same object does
> not go through that proxy, so the called method runs without the transaction it asks for.
>
> **How to fix it:** put @Transactional on the method that is called from outside, or move the transactional method
> into its own bean and call it through that bean.

A few more of its **63 rules**:

| | |
|---|---|
| ☕ **Java** | SQL built by string concatenation, hard-coded passwords and keys, MD5 or SHA-1 for security, unsafe deserialization, empty `catch` blocks, methods that return `null`, classes that should be records |
| 🌱 **Spring Boot** | `@Async` or `@Cacheable` that never takes effect, HTTP clients without timeouts, remote calls inside a transaction, N+1 queries, eager collections, `@Data` on JPA entities, CSRF turned off, actuator open to everyone |
| 🤖 **Spring AI** | user input pasted into system prompts, one chat memory shared by all users, tools that delete data without asking, model output used in SQL or shell commands |
| 🏗️ **Architecture** | controllers calling repositories directly, package cycles, framework code in a hexagonal domain, features reaching into each other |
| 📦 **Dependencies** | libraries with known vulnerabilities (CVEs), Java and Spring Boot versions out of support, `javax` imports that must become `jakarta` |

The [rule catalogue](docs/rules/README.md) lists all of them.

## Quick start

1. Download `spring-inspector-intellij-<version>.zip` from the
   **[latest release](https://github.com/fereshteh-dev/spring-inspector/releases/latest)**. Do not unzip it.
2. In IntelliJ IDEA, open **Settings → Plugins → ⚙ → Install Plugin from Disk…**, choose the zip and restart.
3. Open the **Spring Inspector** tool window at the bottom and click **Inspect Whole project**.

That is all with IntelliJ IDEA 2026.2 or newer. With an older IDE, see [Java 25](#java-25) first.

## Requirements

| | |
|---|---|
| **IDE** | IntelliJ IDEA Community or Ultimate, or another JetBrains IDE, 2025.1 or newer |
| **Java to run the inspector** | Java 25 or newer (see below) |
| **Your projects** | Maven or Gradle projects on Java 21 to 25, Spring Boot 3.3 to 4.x and Spring AI 1.0 to 2.0. They do **not** need to be on Java 25 |

### Java 25

Spring Inspector is built with Java 25 and needs it **to run**. It only reads your source code and never builds or runs
your project, so **the project you review can stay on its own Java version**.

- **IntelliJ IDEA 2026.2 or newer:** nothing to do. The IDE runs on Java 25, and the plugin uses it.
- **Older IDE (2025.1 to 2026.1):** these run on Java 21, so give the plugin a Java 25 once:
  1. Get a JDK 25. The easiest way is inside IntelliJ: **File → Project Structure → SDKs → + → Download JDK…**,
     version **25**, any vendor (for example Eclipse Temurin). You can also download one from
     [adoptium.net](https://adoptium.net/temurin/releases/?version=25).
  2. Open **Settings → Tools → Spring Inspector** and enter the JDK's folder, for example
     `C:\Users\you\.jdks\temurin-25` or `~/.jdks/temurin-25`.
- **Command line and git hook:** `java -version` must show 25 or newer. If another Java is on your `PATH`, call Java 25
  directly: `<path-to-jdk-25>/bin/java -jar spring-inspector.jar ...`

## Using the IntelliJ plugin

- **Inspect:** in the **Spring Inspector** tool window, click **Inspect Whole project**, **Inspect Staged changes** or
  **Inspect Current File**, or right-click files or folders and choose **Inspect with Spring Inspector**. Select a
  finding to read the explanation; double-click it to jump to the code.
- **Fix:** findings are underlined in the editor with an icon in the gutter. Put the caret on one and press
  **Alt+Enter → Apply Spring Inspector fix** to preview the change and apply it.
- **Before every commit:** commits made from IntelliJ are checked first, and you can review the problems or commit
  anyway. Turn this off with **Check with Spring Inspector** in the commit window.
- **Architecture:** the **Architecture** tab, or **Tools → Analyse Architecture with Spring Inspector**, shows how the
  project is structured, draws its parts and their dependencies, and suggests a cleaner structure.

## Using the command line and git hook

Download `spring-inspector-<version>.jar` from the
[latest release](https://github.com/fereshteh-dev/spring-inspector/releases/latest). The examples call it
`spring-inspector.jar`; rename it or use its full name.

```shell
java -jar spring-inspector.jar check --all                   # review the whole project
java -jar spring-inspector.jar check                         # review only your staged changes
java -jar spring-inspector.jar check --base main             # review everything since you branched off main
java -jar spring-inspector.jar check --files src/main/java/com/acme/Orders.java   # review whole files
java -jar spring-inspector.jar check --details security      # explain every finding of one category
java -jar spring-inspector.jar architecture                  # describe the architecture and suggest a cleaner one
java -jar spring-inspector.jar install-hook                  # review every commit automatically
java -jar spring-inspector.jar update                        # download vulnerability advisories now
```

A report looks like this:

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

`--format sarif` produces a report for GitHub code scanning and other CI tools. The [user guide](docs/user-guide.md)
covers every command, CI and troubleshooting.

## Architecture analysis

Spring Inspector recognises how a project is built: layered, package by feature, hexagonal, clean or onion, Spring
Modulith, CQRS, event-driven, multi-module, several applications in one repository, and mixes of these. It checks
that the code keeps to that structure and suggests a cleaner one where it can: a package-by-feature layout built from
your own classes, the cheapest link to cut in a dependency cycle, or which overloaded class to split. Run
`spring-inspector architecture --format markdown` for a report with a diagram.

## Always up to date

Rules about vulnerabilities and versions do not wait for a new Spring Inspector release:

- **Vulnerabilities:** public advisories from [OSV.dev](https://osv.dev), which include the GitHub and Spring
  advisories, are refreshed automatically once a day by the plugin and the git hook, or on demand with `update`.
- **New versions:** the release calendar of Java, Spring Boot and Spring AI comes with the same refresh, so a new
  release or an end of support is known the day it happens.
- **Your dependencies** are read without running your build: from `pom.xml` and Gradle files, parents and BOMs such as
  Spring Boot's, and an application jar you have already built.

The checks themselves work offline, and every report says how old its advisory data is.

## Configure

Run `java -jar spring-inspector.jar init` to create a commented `spring-inspector.yml` in your project. In it you can
turn rules and categories off, change severities and when a commit is blocked, set limits such as the maximum method
length, declare your own layer rules, and ignore generated code. To accept one finding, add a comment with a reason:

```java
// inspector:ignore security.sql-concatenation reason="column name comes from an enum"
```

## Privacy

Spring Inspector never sends your code anywhere and never runs it. The only network access is downloading public
data: vulnerability advisories from OSV.dev and the release calendar from endoflife.date and Maven Central. `update`
does it on demand, and the IntelliJ plugin and the git hook do it at most once a day. Nothing about your project is
sent.

## Status and roadmap

Spring Inspector is young and improving quickly:

- **0.2:** editor highlighting, one-click fixes and a check before every commit in IntelliJ.
- **0.3:** architecture analysis, and fewer false alarms after reviewing every finding on Spring PetClinic, PetClinic
  REST and BuckPal.
- **0.4:** five senior-level Spring rules and a guide to writing your own.
- **Next:** the JetBrains Marketplace, a reviewer mode for pull requests, and published accuracy numbers from more
  open-source projects.

See the [changelog](CHANGELOG.md) and the [specification](docs/SPEC.md) (section 16).

## Contributing

Found a false alarm or missing a rule? [Open an issue](https://github.com/fereshteh-dev/spring-inspector/issues) with
a small code sample, or [write the rule yourself](docs/writing-a-rule.md): the guide walks through it step by step.
See [contributing](CONTRIBUTING.md) for the rest.

### Build from source

Needs JDK 25.

```shell
./mvnw verify                      # builds and tests the command line: inspector-cli/target/spring-inspector.jar
cd inspector-intellij
./gradlew buildPlugin              # builds the plugin: build/distributions/spring-inspector-intellij-<version>.zip
```

To compile the plugin against an IntelliJ you have installed instead of downloading one, add
`-PlocalIde="<path to the IDE>"`. [How it works](docs/architecture.md) explains the design, with diagrams.

## Documentation

- [User guide](docs/user-guide.md): installation, commands, configuration, CI, troubleshooting
- [Rule catalogue](docs/rules/README.md) and [writing a rule](docs/writing-a-rule.md)
- [How it works](docs/architecture.md), with diagrams
- [Specification](docs/SPEC.md) and [architecture decisions](docs/adr/README.md)
- [Contributing](CONTRIBUTING.md), [releasing](docs/releasing.md) and [security policy](SECURITY.md)

## Licence

[Apache License 2.0](LICENSE). Vulnerability data comes from OSV.dev and the GitHub Advisory Database (CC-BY 4.0), and
release dates from endoflife.date (MIT); both are downloaded by each user and are not part of this distribution.
