# Spring Inspector

Offline-first code inspector for Java, Spring Boot and Spring AI projects.

> **Status:** early development. The command line works with a first set of 8 rules; configuration,
> more rules and the web report are next.

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
