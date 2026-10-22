# Rule catalogue

Spring Inspector has 58 rules in six categories. Every finding explains what is wrong, why it matters and how to fix
it, and many come with a patch. Rules know your Java, Spring Boot and Spring AI versions and only run where they apply.

Turn a rule off, change its severity or set its options in `spring-inspector.yml` (see the
[user guide](../user-guide.md#configuration)), or silence one finding with
`// inspector:ignore <rule-id> reason="..."`.

## Security (16)

| Rule | Finds |
|------|-------|
| `security.sql-concatenation` | SQL or JPQL built by concatenating runtime values and then executed |
| `security.weak-cryptography` | MD5, SHA-1 and other broken algorithms used for hashing or encryption |
| `security.insecure-random` | `java.util.Random` or `Math.random()` producing values used for security |
| `security.hardcoded-secret` | Keys, tokens and passwords written in Java code |
| `security.secret-in-settings` | Secrets written in `application*.properties` or `application*.yml` |
| `security.secret-logged` | Secrets passed to log statements |
| `security.permit-all-sensitive` | `permitAll()` on admin, actuator and other sensitive paths |
| `security.csrf-disabled` | CSRF protection turned off in an application that uses sessions |
| `security.actuator-exposed` | Actuator endpoints exposed without protection, such as `shutdown` or `include: "*"` |
| `security.h2-console-enabled` | The H2 console enabled outside a development profile, or open to other machines |
| `security.unsafe-deserialization` | `ObjectInputStream` without a deserialization filter, and data that chooses which classes are created |
| `security.xxe` | XML parsers that resolve external entities |
| `security.path-traversal` | File paths built from request input |
| `security.ssrf` | Outgoing HTTP requests to URLs taken from request input |
| `security.mass-assignment` | Request bodies bound directly to JPA entities |
| `security.vulnerable-dependency` | Libraries with published vulnerabilities (CVE/GHSA), on the line that declares them, with the version that fixes them |

## Spring Boot (14)

| Rule | Finds |
|------|-------|
| `spring.field-injection` | `@Autowired` fields instead of constructor injection |
| `spring.autowired-constructor` | `@Autowired` on a single constructor, where it is not needed |
| `spring.transactional-self-invocation` | `@Transactional` methods called from the same class, so no transaction starts |
| `spring.transactional-not-proxied` | `@Transactional` on private or final methods, or in final classes, where it has no effect |
| `spring.transactional-read-only` | Query-only transactional methods without `readOnly = true` |
| `spring.entity-in-controller` | JPA entities returned from or accepted by controllers |
| `spring.missing-valid` | Request bodies that are not validated with `@Valid` |
| `spring.find-all-unpaged` | `findAll()` without paging on repositories |
| `spring.n-plus-one` | Lazy collections loaded one by one inside loops |
| `spring.blocking-in-reactive` | Blocking calls inside reactive chains |
| `spring.virtual-thread-pinning` | `synchronized` blocks around blocking calls that pin virtual threads (Java 21–23) |
| `spring.rest-template` | `RestTemplate` in new code where `RestClient` is the modern choice |
| `spring.jackson2-in-boot4` | Jackson 2 (`com.fasterxml.jackson`) imports in a Spring Boot 4 project |
| `spring.value-sprawl` | Many `@Value` fields that belong in a `@ConfigurationProperties` record |

## Spring AI (5)

| Rule | Finds |
|------|-------|
| `springai.system-prompt-concatenation` | User input concatenated into system prompts (prompt injection) |
| `springai.shared-conversation-id` | One chat memory conversation shared by all users |
| `springai.unconfirmed-destructive-tool` | Tools the model can call that write or delete without confirmation |
| `springai.full-prompt-logging` | Full prompts and responses written to logs |
| `springai.llm-output-to-sink` | Model output used directly in SQL, shell commands, file paths or HTML |

## Clean code (12)

| Rule | Finds |
|------|-------|
| `cleancode.long-method` | Methods longer than the limit (default 60 lines) |
| `cleancode.deep-nesting` | Deeply nested blocks |
| `cleancode.large-class` | Classes that do too much |
| `cleancode.long-parameter-list` | Methods with too many parameters (default 7) |
| `cleancode.swallowed-exception` | Empty `catch` blocks |
| `cleancode.generic-catch` | `catch (Exception e)` and similar catch-alls |
| `cleancode.return-null` | Public methods that return `null` |
| `cleancode.optional-misuse` | `Optional` used as a field or as a parameter of public methods |
| `cleancode.unused-private` | Private fields and methods nobody uses |
| `cleancode.data-class-to-record` | Data classes that can be records (Java 16+) |
| `cleancode.instanceof-chain` | `instanceof` chains that a pattern-matching `switch` replaces (Java 21+) |
| `cleancode.thread-pool-for-io` | Fixed thread pools for blocking I/O where virtual threads fit (Java 21+) |

## Architecture (5)

The architecture rules map the whole project from its `package` and `import` lines (fast, without compiling), work
out each class's layer from its Spring stereotype, its name or its package, and report on the import statement that
causes the problem, so a review shows the line to change.

| Rule | Finds |
|------|-------|
| `architecture.layer-violation` | Dependencies against the layers: a controller using a repository directly, a service depending on web classes, a repository depending on services, domain classes depending on outer layers |
| `architecture.package-cycle` | Packages that depend on each other in a circle |
| `architecture.domain-depends-on-framework` | In a hexagonal (ports and adapters, clean, onion) project, domain classes that import Spring, JPA, Hibernate, Jackson or the Servlet API |
| `architecture.feature-internals` | In an application organised by feature, a feature that uses another feature's inner classes instead of the classes in its own package (the boundary Spring Modulith checks) |
| `architecture.new-feature-dependency` | A change that makes one feature depend on another for the first time, for the reviewer to confirm |

Teams can declare their own layer rules, which replace the built-in ones for the layers they name:

```yaml
rules:
  options:
    architecture.layer-violation:
      web: service, domain        # the web layer may use only services and the domain
      service: repository, domain
      domain: ""                  # the domain uses no other layer
```

Run `spring-inspector architecture`, or open the **Architecture** tab in IntelliJ, for the whole picture: the
styles the project follows, a diagram of its parts, cycles, overloaded classes and suggestions for a cleaner
structure. See the [user guide](../user-guide.md#architecture).

## Migration (6)

Information to plan upgrades; these findings never block a commit by default. The rules about support dates and
newest releases read the release calendar that `update` downloads (from endoflife.date and Maven Central), so they
stay correct as new versions come out, without a new version of Spring Inspector.

| Rule | Finds |
|------|-------|
| `migration.spring-boot-support` | A Spring Boot line that is out of open-source support, or leaves it within three months |
| `migration.newer-patch` | A Spring Boot or Spring AI version with a newer patch release in the same line |
| `migration.java-support` | A Java version that no longer gets free updates, with the longest-supported one to move to |
| `migration.javax-namespace` | `javax.*` imports that moved to `jakarta.*` |
| `migration.web-security-configurer-adapter` | `WebSecurityConfigurerAdapter`, removed in Spring Security 6 |
| `migration.spring-ai-1x-api` | Spring AI 1.x classes removed in Spring AI 2.0 |
