1. Product
   Problem. Developers now write a lot of code with AI agents. Generic linters do not understand Spring Boot or Spring AI, SonarQube needs a server, and cloud AI reviewers send code outside the company. Seniors need a fast, private, Spring-aware gate before every commit.
   Promise. Install the IntelliJ plugin from GitHub (or run spring-inspector check, or just git commit) and within seconds get a report card:
   Category
   Result
   Security
   ❌ 2 issues
   Spring Boot rules
   ✅ passed
   Spring AI rules
   ❌ 1 issue
   Clean code
   ⚠️ 3 hints
   Architecture
   ✅ passed
   Opening a category lists its issues. Every issue has exactly three parts:
1. What's wrong — file, line range, code snippet, one-sentence description.
2. Why it matters — concrete risk or cost; links to CWE/CVE/OWASP or Spring docs where relevant.
3. How to fix it — a concrete patch (unified diff) the user can apply with one action, plus a short explanation.
   Non-goals (v1). No cloud service, no account, no running the inspected code, no languages other than Java (Kotlin later).
2. Supported inspected projects
   • Java 21 to 25 (detect from maven.compiler.release, toolchains, Gradle toolchain, or .java-version). Rules are version-aware: e.g. only suggest records/pattern matching/virtual threads/ScopedValue when the project's Java level supports them.
   • Spring Boot 3.3–3.5 and 4.0–4.x; Spring AI 1.0–1.1 and 2.0.x. Detect versions from Maven/Gradle files (parse the BOM/parent; never execute the build).
   • A rule declares the version ranges it applies to; the engine skips rules that don't apply. Migration hints (e.g. Boot 3.5 is out of OSS support; Jackson 2 → 3) are a separate INFO-level category.
3. Delivery modes (build order)
1. CLI — spring-inspector check [--staged | --base main | --all] [--format terminal|json|sarif]; also init, update and install-hook.
2. Git pre-commit hook — spring-inspector install-hook; checks staged changes; blocks the commit only on BLOCKER findings (configurable); --no-verify still works.
3. IntelliJ plugin — the main way developers use it. Installs from a zip published on GitHub Releases into IntelliJ IDEA and the other JetBrains IDEs (2025.1+). Tool window with the same report card, findings tree, details with the three parts, open-in-editor; then editor highlighting, quick-fixes that apply the patch, and a check before commit. It runs the bundled CLI in its own process and reads its JSON report (ADR-0004).
   Dropped: the local web report (spring-inspector ui). The IDE plugin replaces it.
4. Modules (Maven multi-module)
   • inspector-core — domain + ports + engine. Pure Java.
   • inspector-java-model — parses source into a semantic model (OpenRewrite LST or JavaParser with symbol solver; decide in Phase 1 and record an ADR).
   • inspector-rules-security, inspector-rules-spring, inspector-rules-springai, inspector-rules-cleancode, inspector-rules-architecture — check plugins.
   • inspector-advisories — offline vulnerability/advisory database and sync.
   • inspector-llm — optional local AI explainer via Spring AI 2.0 + Ollama.
   • inspector-git — JGit: staged/base diffs, hook installer.
   • inspector-cli — picocli entry point, terminal, JSON and SARIF renderers; the executable jar.
   • inspector-intellij — the IntelliJ plugin; a separate Gradle build that bundles the CLI jar (ADR-0004).
   • inspector-eval — benchmark harness.
5. Domain model (inspector-core)
   • ProjectContext — root path, detected Java/Boot/Spring AI versions, build tool, modules, config.
   • ChangeSet → FileChange → Hunk (only changed lines are reported by default; whole-file context is available to checks).
   • Check (port) — id, category, applicable version ranges, inspect(InspectionInput) -> List<Finding>.
   • Finding — ruleId, category, severity (BLOCKER/MAJOR/MINOR/INFO), location, WhatsWrong, WhyItMatters, HowToFix (with optional Patch), confidence, source (RULE | ADVISORY | AI).
   • Category — SECURITY, SPRING_BOOT, SPRING_AI, CLEAN_CODE, ARCHITECTURE, MIGRATION (sealed).
   • ReportCard — per category: PASSED / FAILED / HINTS, counts, findings; overall score and gate decision.
   • Ports: SourceProvider, Check, AdvisorySource, Explainer, ReportRenderer, FixApplier.
6. Pipeline
1. Detect project context and versions.
2. Collect the change set (staged, against base branch, or all).
3. Build the semantic model for affected files (cache by file hash in .spring-inspector/cache).
4. Run applicable checks in parallel on virtual threads, with a per-check timeout.
5. Match dependencies against the local advisory database.
6. Optionally ask the local LLM to (a) explain findings in plain words and (b) find design issues rules cannot express — LLM findings are clearly marked AI, lower confidence, never BLOCKER.
7. Deduplicate, filter to changed lines, rank, cap per category.
8. Build the ReportCard, apply the gate, render.
   Target: under 5 seconds for a typical commit without AI; AI explanations stream in afterwards.
7. Rule catalogue (v1 — at least 40 rules, each with tests and fixture code)
   Security
   • SQL/JPQL built by string concatenation; unsafe @Query with SpEL from user input.
   • Spring Security misconfig: permitAll on sensitive paths, CSRF disabled for browser apps, actuator endpoints exposed without auth, /h2-console enabled in non-dev profile.
   • Secrets: hard-coded keys, tokens, passwords in code and application*.yml; secrets written to logs.
   • Unsafe deserialization, XXE-prone parsers, path traversal in file handling, SSRF via RestClient/WebClient with user-controlled URLs.
   • Weak crypto and randomness (MD5/SHA-1 for passwords, Random for tokens).
   • Mass assignment: binding request bodies directly into JPA entities.
   • Vulnerable dependencies from the advisory database, on the line that declares them, with the lowest version that fixes every advisory. (A reachability hint is deferred: OSV's Maven data does not say which API is vulnerable.)
   Spring Boot
   • Field injection; @Autowired on constructors needlessly.
   • @Transactional self-invocation, on private methods, or on non-public/final classes; read-only missing on query methods.
   • Entities exposed from controllers; missing validation (@Valid) on request bodies.
   • N+1 risks (lazy collections accessed in loops), findAll() without paging on large tables.
   • Blocking calls inside reactive chains; misuse of virtual threads with synchronized pinning (version-aware).
   • Old APIs: RestTemplate in new code, WebSecurityConfigurerAdapter, Jackson 2 imports in Boot 4 projects.
   • Configuration: @Value sprawl instead of @ConfigurationProperties records.
   Spring AI
   • API keys in code or committed config instead of environment variables.
   • User input concatenated into system prompts (prompt-injection risk); no separation of instructions and data.
   • Tools exposed to the model that perform writes/deletes without confirmation or authorisation checks.
   • No token/cost limits, timeouts or retry bounds on ChatClient calls; unbounded tool-call loops.
   • Logging full prompts/responses containing personal data; missing redaction advisor.
   • Structured output not validated; LLM output used directly in SQL, shell, file paths or HTML.
   • Shared chat memory without per-user/tenant isolation.
   • Version-aware API checks (1.x-only APIs in a 2.0 project and vice versa).
   Clean code
   • Long methods, deep nesting, large classes, long parameter lists (thresholds configurable).
   • Boilerplate that modern Java removes: getters/setters/equals for pure data → record; instanceof chains → pattern matching switch; manual thread pools for I/O → virtual threads (only when the project's Java level allows).
   • Swallowed exceptions, generic catch (Exception), returning null from public methods, Optional misuse.
   • Magic numbers/strings, dead code, misleading names.
   Architecture
   • The user declares architecture in spring-inspector.yml (layers or hexagonal or Spring Modulith modules) or the tool infers a baseline on first run and asks the user to confirm it.
   • Violations: forbidden layer dependencies, cycles between packages/modules, controllers calling repositories directly, domain depending on frameworks, new cross-module dependencies.
   • Architecture diff: for each run, compare the dependency graph before and after the change and report new edges, removed edges and new cycles, with a small rendered diagram in the web UI.
8. Security advisories ("always current")
   • One compact, checksummed file in ~/.spring-inspector/advisories, loaded in milliseconds (ADR-0003; replaces the SQLite/H2 idea).
   • spring-inspector update downloads OSV.dev's Maven archive, which already includes GitHub Security Advisories and the Spring advisories. It downloads only when the archive changed (ETag), verifies the published checksum, and works behind a proxy.
   • Dependencies are found without running the build: build files, parents and BOMs from the local Maven/Gradle cache, and libraries inside an already built Spring Boot jar or war.
   • Every report shows the advisory database date and warns if it is older than 7 days.
   • Fully offline after sync; no code ever leaves the machine.
9. Local AI (optional, off by default)
   • Spring AI 2.0 ChatClient with the Ollama starter; model configurable (a local coding model).
   • Used only to explain findings in friendlier words, propose patches for rule findings, and spot design smells; AI suggestions are validated (patch must apply and compile-check via the parser) before being shown.
   • Prompts are text blocks in resources; inspected code is wrapped as data and prompts instruct the model to ignore instructions within it.
   • Works without AI: every rule finding already contains all three parts.
10. The fun part
    • A mascot persona ("Spring Sensei") with short, kind, slightly cheeky reactions to the score; tone selectable: serious | friendly | sensei.
    • Streaks and badges stored locally: "10 clean commits", "Zero-secret week", "Architecture keeper".
    • spring-inspector roast — humorous summary of the worst issue; never insulting, never about the person.
11. UI
    • Terminal: colourful report card with emoji status, collapsible detail via --details <category>.
    • IntelliJ: tool window with the report card, category tree, the three parts side by side with the patch, open-in-editor; editor highlighting and quick-fixes; uses the IDE's theme, so dark mode and accessibility come from the platform.
    • The architecture diff appears in the report as findings and, later, as a diagram in the tool window.
12. Configuration (spring-inspector.yml in the inspected repo)
    • Enabled categories and rules, severities, thresholds, gate policy, architecture definition, ignored paths, AI on/off and model, tone.
    • Inline suppression: // inspector:ignore <ruleId> reason="..." — reason required.
13. Quality bar for this repository
    • Tech: Java 25 LTS, Spring Boot 4.1.x, Spring AI 2.0.x, picocli, JGit, OpenRewrite or JavaParser, JUnit (latest supported by Boot 4.1), AssertJ, Mockito, ArchUnit.
    • Build: Maven wrapper, Spotless (palantir-java-format), Error Prone + NullAway, JaCoCo (≥ 80% on core and rules), CycloneDX SBOM, Dependabot.
    • Every rule: description, bad example, good example, tests, docs page generated from the rule metadata.
    • Distribution: every release on GitHub Releases carries the IntelliJ plugin zip and the executable CLI jar; the JetBrains Marketplace once the plugin is verified against the IDEs it supports; native image, Homebrew/SDKMAN/Scoop and a Docker image for CI later.
    • Outputs SARIF so it also works in GitHub code scanning.
14. Evaluation harness (inspector-eval)
    • At least 60 fixture projects/snippets across Java 21 and 25 and Boot 3.5 / 4.1: labelled vulnerable, labelled clean.
    • Reports precision, recall and false-positive rate per category and per rule into eval-report.md; CI fails if precision drops below the configured threshold.
    • This is the project's differentiator — publish the numbers in the README.
15. Open-source readiness
    • Apache-2.0 licence, README (problem, GIF demo, install from GitHub Releases, quick start, report-card screenshot, rule list, comparison with SonarQube/PMD/Semgrep, eval numbers), user guide, CONTRIBUTING (how to add a rule in under 30 minutes), SECURITY.md, CODE_OF_CONDUCT, CHANGELOG, semantic versioning, GitHub Actions for CI and releases (a version tag builds, tests and publishes the plugin zip and CLI jar), issue/PR templates, docs/adr/ with architecture decision records.
    • Clean plugin API for third-party rule packs — this is what makes it acquisition-worthy for an IDE vendor.
16. Roadmap after the first release
    • v0.1 (first public release): CLI, git hook, 51 rules, vulnerable dependencies, IntelliJ tool window.
    • v0.2: IntelliJ editor highlighting, quick-fixes, check before commit; Plugin Verifier; JetBrains Marketplace.
    • v0.3: architecture: style recognition (layered, package by feature, hexagonal, clean, Spring Modulith, CQRS, event-driven, multi-module), architecture rules, report with diagram and suggestions, IntelliJ Architecture tab.
    • v0.4: evaluation harness with published precision/recall; reviewer mode (review a branch) in IntelliJ.
    • Later: optional local AI explanations (section 9), the fun part (section 10), Kotlin, more distribution channels.
