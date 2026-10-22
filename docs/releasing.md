# Releasing

## A new version

1. Add the version's section at the top of [CHANGELOG.md](../CHANGELOG.md) with today's date. The plugin's
   "What's new" text is generated from it.
2. Commit, then tag and push:

   ```shell
   git tag -a v0.2.0 -m "Spring Inspector 0.2.0"
   git push origin v0.2.0
   ```

The [release workflow](../.github/workflows/release.yml) then sets the version from the tag, builds and tests
everything, checks the plugin with JetBrains' Plugin Verifier, and publishes a GitHub release with the plugin zip, the
command-line jar and `SHA256SUMS`. When the `PUBLISH_TOKEN` secret is set, it also uploads the plugin to the
JetBrains Marketplace.

## Keeping the rules current

Support dates, patch releases and vulnerability advisories reach users without a new version: `update`, the git hook
and the plugin download them. New APIs and removals need new or changed rules. The
[release watch](../.github/workflows/release-watch.yml) workflow checks every Monday for new Java, Spring Boot and
Spring AI release lines and opens an issue such as *Review rules for Spring Boot 4.2* with a checklist. Run it by hand
from **Actions → Release watch → Run workflow**.

## The JetBrains Marketplace

Publishing on the [JetBrains Marketplace](https://plugins.jetbrains.com) lets developers install the plugin from
**Settings → Plugins → Marketplace** and get updates automatically. It needs a JetBrains account, and the first
version is uploaded by hand.

1. Sign in to [plugins.jetbrains.com](https://plugins.jetbrains.com) with your JetBrains account and accept the
   developer agreement.
2. Choose **Upload plugin**, select `spring-inspector-intellij-<version>.zip` from a GitHub release, set the licence to
   Apache 2.0 and the source code link to the GitHub repository, and submit. JetBrains reviews new plugins, usually
   within a few working days.
3. Once it is approved, create a token under **your profile → My Tokens → Generate Token**.
4. In the GitHub repository, add it under **Settings → Secrets and variables → Actions → New repository secret** with
   the name `PUBLISH_TOKEN`.

From then on, every version tag publishes the plugin to the Marketplace as well. JetBrains signs plugins it
distributes, so no signing certificate is needed.
