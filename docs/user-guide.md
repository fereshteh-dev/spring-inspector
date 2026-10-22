# User guide

- [Installation](#installation)
- [Using the IntelliJ plugin](#using-the-intellij-plugin)
- [Using the command line](#using-the-command-line)
- [Vulnerable dependencies](#vulnerable-dependencies)
- [Configuration](#configuration)
- [Continuous integration](#continuous-integration)
- [Troubleshooting](#troubleshooting)

## Installation

Every release on [GitHub Releases](https://github.com/fereshteh-dev/spring-inspector/releases/latest) has:

- `spring-inspector-intellij-<version>.zip`: the IntelliJ plugin;
- `spring-inspector-<version>.jar`: the command line, which also installs the git hook;
- `SHA256SUMS`: checksums. Verify a download with `sha256sum -c SHA256SUMS --ignore-missing` (Linux, macOS) or
  `Get-FileHash <file>` (Windows PowerShell).

### Requirements

- **Java 25 or newer** to run the inspector. Check with `java -version`. Any JDK 25 distribution works, for example
  [Eclipse Temurin](https://adoptium.net).
- For the plugin: **IntelliJ IDEA 2025.1 or newer** (Community or Ultimate), or another JetBrains IDE of the same
  version.
- The projects you inspect can use **Java 21 to 25**, **Spring Boot 3.3 to 4.x** and **Spring AI 1.0 to 2.0**, built
  with Maven or Gradle.

### Install the plugin

1. Download `spring-inspector-intellij-<version>.zip` and keep it zipped.
2. Open **Settings → Plugins → ⚙ → Install Plugin from Disk…** and choose the zip.
3. Restart the IDE.
4. If your IDE runs on a Java older than 25 (all 2025.x versions), open **Settings → Tools → Spring Inspector** and
   enter the path of a JDK 25 (its home folder, or its `bin/java` file). IntelliJ IDEA 2026.2 runs on Java 25
   already; leave the field empty there.

To update, install the new zip the same way. To remove it, uninstall **Spring Inspector** in **Settings → Plugins**.

### Install the command line

Save the jar somewhere permanent, for example `~/tools/spring-inspector.jar`, and run it with
`java -jar ~/tools/spring-inspector.jar`. A shell alias saves typing:

```shell
alias spring-inspector='java -jar ~/tools/spring-inspector.jar'          # Linux, macOS
function spring-inspector { java -jar $HOME\tools\spring-inspector.jar @args }   # Windows PowerShell profile
```

The examples below use `spring-inspector` for `java -jar spring-inspector.jar`.

## Using the IntelliJ plugin

Open the **Spring Inspector** tool window at the bottom of the IDE.

| Button | Does |
|--------|------|
| ▶ **Inspect Whole project** | Reviews every file of the project |
| **Inspect Staged changes** | Reviews only what you have staged for the next commit (needs git) |
| **Inspect Current File** | Reviews the whole file open in the editor |
| ⚙ **Settings** | Opens Settings → Tools → Spring Inspector |

The plugin saves your open files and then runs in the background; you can keep working. When it finishes:

- the line at the top shows the score, whether the gate passes, and how many findings there are;
- the tree on the left groups findings by category, with the most severe first;
- selecting a finding shows **what's wrong**, **why it matters** (with links to the advisory, OWASP or the Spring
  documentation) and **how to fix it**, plus a patch when there is one;
- double-click a finding, or press **Enter**, to open the code at that line;
- notes at the bottom of the tree say when something needs attention, for example old vulnerability advisories.

**Tools → Inspect Project with Spring Inspector** opens the tool window and reviews the whole project in one step.
To review some files or folders, select them in the project view (or right-click in the editor) and choose
**Inspect with Spring Inspector**.

### In the editor

After a run, findings are shown in the code like the IDE's own problems: underlined, marked in the scroll bar, and
with an icon in the gutter. Hover over the icon to read the finding; click it to see all its details in the tool
window. The marks follow your edits until the next run.

When a finding comes with a fix, put the caret on its line and press **Alt+Enter → Apply Spring Inspector fix**. The
preview shows the exact change before you apply it, and **Ctrl+Z** undoes it. If the code changed since the run, the
fix is refused instead of being applied in the wrong place; run the inspection again.

### Before a commit

When you commit from IntelliJ, Spring Inspector first reviews the files in the commit. If the gate blocks, you can
**Review** the problems (the commit stops and the tool window shows them), **Commit Anyway**, or **Cancel**. Turn the
check on or off with **Check with Spring Inspector** in the commit window's options, or in
**Settings → Tools → Spring Inspector**; the choice is remembered.

The plugin uses the same rules and the same `spring-inspector.yml` as the command line, so both always agree.

New rules come with new versions. Once a day, after a run, the plugin asks GitHub whether a newer version exists and
says so, with a **Download** link; only the public release list is requested. Turn this off in
**Settings → Tools → Spring Inspector**. Each
category shows at most 20 findings by default; set `max-findings-per-category` higher to see more.

## Using the command line

```
spring-inspector check [--staged | --base <ref> | --all | --files <file>...] [--details <category>]
                       [--format terminal|json|sarif]
                       [--project <dir>] [--config <file>] [--no-cache]
spring-inspector init [--project <dir>]
spring-inspector install-hook [--force] [--project <dir>]
spring-inspector update [--max-age <age>] [--quiet]
```

### check

| Option | Meaning |
|--------|---------|
| `--staged` | Review staged changes. This is the default. |
| `--base main` | Review everything changed since your branch left `main`. Good for pull requests. |
| `--all` | Review every file. Works also for projects that are not in git. |
| `--files <file>...` | Review these whole files, staged or not. Paths are relative to the project or absolute. |
| `--details security` | Explain every finding of one category: `security`, `spring-boot`, `spring-ai`, `clean-code`, `architecture`, `migration`. |
| `--format json` / `sarif` | Machine-readable output for other tools and for GitHub code scanning. |
| `--project <dir>` | Review another directory instead of the current one. |
| `--config <file>` | Use this settings file instead of the project's `spring-inspector.yml`. |
| `--no-cache` | Inspect every file again instead of reusing results for unchanged files. |

By default only findings on the lines you changed are reported, so old code does not drown out what you just wrote.

**Exit codes:** `0` the gate passed, `1` the gate blocked (a BLOCKER finding by default), `2` wrong usage, `3` the
tool itself failed.

### install-hook

Installs a git pre-commit hook that runs `check --staged` before every commit and stops the commit when the gate
blocks. To commit anyway once, use `git commit --no-verify`. An existing hook is left alone unless you pass
`--force`; it is then kept as a backup.

### init

Writes a commented `spring-inspector.yml` with the defaults to the project. Commit it so your whole team uses the same
settings.

## Vulnerable dependencies

```shell
spring-inspector update
```

Downloads the public vulnerability advisories for Maven libraries from [OSV.dev](https://osv.dev) to
`~/.spring-inspector/advisories` (about 10 MB, stored in 1.5 MB). It includes the GitHub Security Advisories and the
Spring advisories. Later runs download only when the data changed. Behind a proxy, set `HTTPS_PROXY`, for example
`HTTPS_PROXY=http://proxy.example.com:3128`.

`check` then matches your project's libraries against them whenever a build file is part of the review (always with
`--all`). It finds the versions without running your build:

- versions written in `pom.xml`, `build.gradle(.kts)`, `gradle.properties` and `gradle/libs.versions.toml`;
- versions that a parent or BOM sets, such as Spring Boot's, read from your local Maven (`~/.m2`) or Gradle cache;
- every library, including indirect ones, inside an application jar or war you have built in `target/` or
  `build/libs/`.

`update` also downloads the release calendar of Java (as Eclipse Temurin publishes it), Spring Boot (both from
[endoflife.date](https://endoflife.date)) and Spring AI (from Maven Central). The migration rules use it to say when
a Spring Boot or Java line leaves free support and when a newer patch release is out. Without it, they fall back to
built-in dates or stay quiet.

Each finding sits on the line that declares the library and names the lowest version that fixes all of its known
vulnerabilities. Libraries whose version cannot be worked out are listed, never silently skipped; building the
project once usually fixes that. Every report shows the date of the advisories and warns when they are more than 7
days old.

You rarely need to run `update` yourself: the IntelliJ plugin refreshes the advisories in the background when they
are more than a day old (turn this off in **Settings → Tools → Spring Inspector**), and the git hook runs
`update --max-age 1d --quiet` before each check, which downloads at most once a day and never blocks a commit. A
failed download is not retried for an hour, so working offline stays fast. Reinstall the hook with `install-hook`
to get this in hooks installed by older versions.

## Configuration

`spring-inspector init` writes this file as `spring-inspector.yml` in the project root. Every setting is optional.

```yaml
gate:
  block-on: blocker            # block when a finding is at least: blocker, major, minor or info
  # minimum-score: 70          # also block when the score drops below this

categories:
  disabled: []                 # security, spring-boot, spring-ai, clean-code, architecture, migration

rules:
  disabled: []                 # for example [spring.field-injection]
  severity: {}                 # for example { security.weak-cryptography: blocker }
  options:
    cleancode.long-method:
      max-lines: 60

ignore:                        # files that are never inspected; ** matches any number of folders
  - "**/generated/**"
  - "**/generated-sources/**"

max-findings-per-category: 20
check-timeout: 5s              # stop a rule that takes longer than this
changed-lines-only: true       # false: report findings anywhere in the changed files
```

Unknown settings are reported as warnings; invalid values stop the run and name the setting.

### Accepting a finding

When a finding is wrong for your code, or you accept the risk, put this comment on its line or the line above:

```java
// inspector:ignore security.sql-concatenation reason="column name comes from an enum"
```

The reason is required. Suppressed findings still appear in the report as suppressed (and in SARIF), so reviewers can
see them.

## Continuous integration

Review every pull request and show the findings in GitHub's **Security → Code scanning**:

```yaml
name: Spring Inspector
on: pull_request
permissions:
  contents: read
  security-events: write
jobs:
  inspect:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v7
        with:
          fetch-depth: 0
      - uses: actions/setup-java@v6
        with:
          distribution: temurin
          java-version: '25'
      - name: Inspect
        run: |
          gh release download --repo fereshteh-dev/spring-inspector --pattern 'spring-inspector-*.jar' --output spring-inspector.jar
          java -jar spring-inspector.jar update
          java -jar spring-inspector.jar check --base origin/${{ github.base_ref }} --format sarif > spring-inspector.sarif
        env:
          GH_TOKEN: ${{ github.token }}
      - uses: github/codeql-action/upload-sarif@v4
        if: always()
        with:
          sarif_file: spring-inspector.sarif
```

The step fails when the gate blocks, which stops the pull request from being merged if the check is required.

## Troubleshooting

**"Spring Inspector needs Java 25 or newer"** in IntelliJ: your IDE runs on an older Java. Install a JDK 25 and enter
its path in **Settings → Tools → Spring Inspector**.

**`UnsupportedClassVersionError`** on the command line: `java` is older than 25. Check `java -version`, or call a JDK
25 directly: `<jdk-25>/bin/java -jar spring-inspector.jar`.

**"Nothing to inspect"**: nothing is staged. Stage your changes, or use `--all` or `--base main`.

**"Not inside a git repository"**: `--staged` and `--base` need git. Use `--all` or `--files` for a project that is
not in git.

**Alt+Enter does not offer the fix**: only findings marked *fix available* have one, and the caret must be on the
finding's line. If the file changed since the run, run the inspection again.

**"No vulnerability advisories on this machine"**: run `spring-inspector update` once.

**"N libraries were not checked for vulnerabilities"**: their versions come from a parent or BOM that is not in your
local Maven or Gradle cache. Build the project once, or write the versions in the build file.

**A category shows "N more not shown"**: raise `max-findings-per-category` in `spring-inspector.yml`.

**The results look out of date**: run `check --no-cache` once to inspect every file again.

Found a bug or a wrong finding? [Open an issue](https://github.com/fereshteh-dev/spring-inspector/issues) with the
rule id and a small code sample. For security problems in Spring Inspector itself, follow the
[security policy](../SECURITY.md).
