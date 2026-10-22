# Writing a rule

This guide adds a rule from start to finish. As the example, we write `spring.system-exit`, which reports
`System.exit()` in a Spring application, where it kills the JVM without closing the application context: no
`@PreDestroy`, no graceful shutdown, no flushed logs.

A rule is one small class with a test. Most rules take under an hour; making sure a rule raises **no false alarms** takes
longer, and that matters more.

## 1. Pick the module and the id

| Module | Category | Id prefix |
|--------|----------|-----------|
| `inspector-rules-security` | `SECURITY` | `security.` |
| `inspector-rules-spring` | `SPRING_BOOT` or `MIGRATION` | `spring.` or `migration.` |
| `inspector-rules-springai` | `SPRING_AI` | `springai.` |
| `inspector-rules-cleancode` | `CLEAN_CODE` | `cleancode.` |
| `inspector-rules-architecture` | `ARCHITECTURE` | `architecture.` |

The id is the prefix and a short, lower-case name of the problem: `spring.system-exit`. It appears in reports, in
`spring-inspector.yml` and in `inspector:ignore` comments, so it never changes once released.

## 2. Write the check

Rules that read Java code extend `JavaCheck` and get one parsed file at a time. Put the class next to the other rules
of the module:

```java
package io.github.fereshtehdev.springinspector.rules.spring;

/** {@code System.exit()} in application code, which stops the JVM without closing the Spring context. */
public final class SystemExitCheck extends JavaCheck {

    static final RuleId ID = RuleId.of("spring.system-exit");

    private static final WhyItMatters WHY = new WhyItMatters(
            "System.exit() stops the JVM at once. Spring does not close the application context, so @PreDestroy"
                    + " methods do not run, requests in progress are cut off and buffered logs can be lost.",
            List.of(SpringCode.springDocs(
                    "Spring Boot: Application exit",
                    "spring-boot/reference/features/spring-application.html#features.spring-application.application-exit")));

    private static final HowToFix FIX = HowToFix.withoutPatch(
            "Throw an exception, or close the context with SpringApplication.exit(context) and pass its exit code"
                    + " to System.exit only in main().");

    @Override
    public RuleId id() {
        return ID;
    }

    @Override
    public Category category() {
        return Category.SPRING_BOOT;
    }

    @Override
    protected List<Finding> inspect(JavaFile file) {
        return file.unit().findAll(MethodCallExpr.class, call -> isSystemExit(file, call)).stream()
                .filter(call -> !insideMain(call))
                .map(call -> finding(
                        file, call, Severity.MAJOR, Confidence.HIGH, "System.exit() stops the JVM without closing Spring", WHY, FIX))
                .toList();
    }

    private static boolean isSystemExit(JavaFile file, MethodCallExpr call) {
        return call.getNameAsString().equals("exit")
                && call.getScope().filter(scope -> file.refersTo(scope.toString(), "java.lang.System")).isPresent();
    }

    private static boolean insideMain(MethodCallExpr call) {
        return Nodes.ancestor(call, MethodDeclaration.class)
                .filter(method -> method.getNameAsString().equals("main") && method.isStatic())
                .isPresent();
    }
}
```

What the parts are for:

- **`finding(...)`** places the finding on the node's lines and records what is wrong, why it matters and how to fix
  it. Every finding needs all three. Write them for a developer who has never heard of the problem: plain words, the
  consequence they will see, and the change to make.
- **Severity:** `BLOCKER` for problems that are exploitable or lose data (by default these block a commit), `MAJOR`
  for bugs and serious risks, `MINOR` for maintainability, `INFO` for advice.
- **Confidence:** `HIGH` when the code certainly has the problem, `MEDIUM` when it probably does (for example, when
  a type could not be resolved), `LOW` for hints.
- **`file.refersTo(name, qualifiedName)`** checks a name through the file's imports. It works without the project's
  jars. `file.typeOf(expression)` and `file.declaringTypeOf(call)` resolve types more precisely, but return empty
  when they cannot, so always have a fallback.
- **`file.hasAnnotation(node, "org.springframework...Annotation")`** matches annotations through the imports.
- **Test code** is inspected by every category except clean code. Override `inspectsTestSources()` to change that.

### Offering a fix

When the change is mechanical and safe, attach a patch. The IDE shows it behind **Alt+Enter**:

```java
List<TextEdit> edits = new ArrayList<>();
edits.add(file.replace(node, "replacement text"));
file.addImport("java.time.Duration").ifPresent(edits::add);
HowToFix fix = HowToFix.withPatch("Explanation", file.patch(edits));
```

Only offer a patch that cannot change what correct code does. If the fix needs judgement, describe it instead.

### Options and versions

- Settings from `spring-inspector.yml` arrive through `file.options()`, for example
  `file.options().positiveInteger("max-lines").orElse(60)`.
- Override `applicability()` when a rule only makes sense for some versions:
  `Applicability.always().requiringJava(VersionRange.atLeast(Version.of(21, 0, 0)))`, or `requiringSpringBoot` and
  `requiringSpringAi`. Rules that do not apply are skipped.

### Rules for other files

To read `application.properties` and `application.yml`, extend `SettingsCheck` instead; `ActuatorExposureCheck` is
an example. Checks that read build files implement the `Check` port directly; see `SpringBootSupportCheck`.

## 3. Register it

Add the class name to `src/main/resources/META-INF/services/io.github.fereshtehdev.springinspector.core.port.Check`
of the module. The command line and the plugin find rules through this file.

## 4. Test it

Put two small sample files under `src/test/resources/fixtures/<rule id>/`:

- `bad/` holds code with the problem, in every form the rule must catch;
- `good/` holds the closest code that is correct, including cases that look similar but are fine.

Then test with `CheckTester` from `inspector-rule-testkit`:

```java
@Test
void reportsSystemExitOutsideMain() {
    String bad = resource("fixtures/spring.system-exit/bad/Jobs.java");

    Finding finding = CheckTester.of(new SystemExitCheck()).source(bad).singleFinding();

    assertThat(finding.location().lines().start()).isEqualTo(lineOf(bad, "System.exit(1)"));
}

@Test
void acceptsSystemExitInMain() {
    assertThat(CheckTester.of(new SystemExitCheck())
                    .source(resource("fixtures/spring.system-exit/good/Application.java"))
                    .findings())
            .isEmpty();
}
```

`CheckTester` also has `.file(path, content)` for files at a specific path (such as `src/test/java/...` or
`application.yml`), `.option(key, value)`, `.javaVersion("21")`, `.springBootVersion("4.1.0")`, and
`.fixed(finding)`, which returns the file after the patch is applied.

Test names describe behaviour: `reportsSystemExitOutsideMain`, not `test1`.

## 5. Check it on real projects

Unit tests prove the rule finds what you thought of. Real code shows what you did not. Before opening a pull request,
run the rule on two or three open-source Spring projects and read every finding it raises:

```shell
./mvnw -q install -DskipTests
java -jar inspector-cli/target/spring-inspector.jar check --all --project ../spring-petclinic --details spring-boot
```

If a finding is wrong, narrow the rule and add that case to `good/`. A rule that is right nine times out of ten still
teaches people to ignore it.

Spring Inspector never runs the code it inspects, and neither may a rule: it only reads text and syntax trees, and
treats what it reads as data, never as instructions.

## 6. Document it and open a pull request

1. Add a line to the right table in the [rule catalogue](rules/README.md) and update the counts there and in the
   main README.
2. Add an entry under `[Unreleased]` in the [changelog](../CHANGELOG.md).
3. Run `./mvnw spotless:apply` and then `./mvnw verify`. It checks formatting, Error Prone with NullAway, tests,
   the architecture rules and the 80% coverage gate.
4. In the pull request, say which projects you checked the rule on and what it found.
