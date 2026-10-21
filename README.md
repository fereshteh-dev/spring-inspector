# Spring Inspector

Offline-first code inspector for Java, Spring Boot and Spring AI projects.

> **Status:** early development. The command line works with 50 rules across security, Spring Boot, Spring AI,
> clean code and migration, and per-project settings; the advisory database and the web report are next.

Run it before every commit and get a report card covering security, Spring Boot rules,
Spring AI rules, clean code and architecture. Every finding says what's wrong, why it
matters and how to fix it, with a patch you can apply. Your code never leaves your machine.

## Building

Requires JDK 25 or newer.

```shell
./mvnw verify
```

This builds `inspector-cli/target/spring-inspector.jar`.

## Using it

Run it from your project directory, inside a git repository:

```shell
java -jar spring-inspector.jar check                       # staged changes (the default)
java -jar spring-inspector.jar check --base main           # everything since you branched off main
java -jar spring-inspector.jar check --all                 # the whole project
java -jar spring-inspector.jar check --details security    # why it matters and how to fix it
java -jar spring-inspector.jar check --format sarif        # also: json
java -jar spring-inspector.jar install-hook                # check every commit; skip once with --no-verify
```

## Configuration

Settings live in `spring-inspector.yml` at the project root; `java -jar spring-inspector.jar init` writes a commented
one with the defaults. You can turn rules and categories off, change severities and the gate, set rule options such
as `cleancode.long-method: { max-lines: 80 }`, and ignore paths such as `**/generated/**`. Unknown settings are
warnings; invalid values stop the run with the name of the setting. In CI, `check --config <file>` uses a file of
your choice instead of the one in the repository.

To silence one finding, write this on its line or the line above. The reason is required, and suppressed findings
still appear in the report (and as suppressed results in SARIF), so reviews can audit them:

```java
// inspector:ignore security.sql-concatenation reason="column name comes from an enum"
```

`check` exits with 1 when the gate blocks (a BLOCKER finding by default), 2 for a usage error and 3 if the tool
itself fails. Results are cached per file in `.spring-inspector/cache`, signed with a key in `~/.spring-inspector`,
so unchanged files are not inspected again.

## Documentation

- [Specification](docs/SPEC.md)
- [Architecture decision records](docs/adr/README.md)
- [Rule catalogue](docs/rules/README.md)
- [Contributing](CONTRIBUTING.md)
- [Security policy](SECURITY.md)

## Licence

[Apache License 2.0](LICENSE)
