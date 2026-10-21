# ADR-0002: Signed, dependency-aware findings cache

- **Status:** Accepted
- **Date:** 2026-10-21
- **Deciders:** project maintainers

## Context

SPEC §6 asks for a cache by file hash in `.spring-inspector/cache` and a run under 5 seconds for a typical commit.
On this repository (138 source files, 8 checks) a run without a cache takes 1.1–2.0 s, almost all of it parsing and
type resolution.

Three things make a naive cache wrong:

1. **A check's result can depend on other files.** Type resolution reads the declarations of referenced types, so
   `File A` can produce different findings after `File B` changes, or after a missing type appears.
2. **The cache lives inside the inspected repository, which is untrusted.** A committed cache file could claim a
   vulnerable file is clean.
3. **JavaParser ASTs do not serialize cheaply**, so caching the syntax tree saves little.

## Decision

Cache **findings**, not syntax trees: one entry per check and file. The cache lives in
`javamodel.cache`.

- **Key:** the check's code fingerprint (its jar and the model's jar: name, size, modification time), the run context
  (Java release, detected versions, running JDK, source roots), the file path and the SHA-256 of its content. Classes
  loaded from a directory (IDE, tests) have no stable fingerprint and are never cached.
- **Dependencies:** all symbol resolution goes through `JavaFile`, which runs it under one lock (JavaParser's
  resolver is not thread-safe) and records every project file the type solver reads, with its hash, and every
  candidate path it found absent. An entry is reused only if all of them are unchanged. ArchUnit forbids checks from
  calling JavaParser's resolution methods directly.
- **Integrity:** the file is signed with HMAC-SHA256 under a per-user key that the caller keeps outside the repository
  (the CLI will use `~/.spring-inspector/cache.key`). An unsigned, foreign, corrupt or oversized file reads as an
  empty cache. The format is a length-checked binary layout with a string table, never Java serialization.
- **Placement:** the cache directory must be a real directory inside the project (no links or junctions); it gets a
  `.gitignore` of `*`. Writes go to a temporary file and are moved into place.
- **Lifetime:** entries unused for 30 days are dropped on save, with at most 100,000 entries.
- **Laziness:** the parser and symbol solver are built on first parse, so a fully cached run never loads them.

Patches are built from text edits by our own unified-diff generator (`javamodel.patch`). The edits already say which
lines change, so the diff library ADR-0001 anticipated is not needed.

## Consequences

- On this repository a run with a warm cache takes about 0.26 s of inspection time in a fresh JVM, against
  1.1–2.0 s without it. The cache file is about 66 KB.
- A cache never makes a result wrong as long as checks follow the `JavaCheck` contract: the result depends only on the
  file, types resolved through `JavaFile`, and the run context. Checks that read configuration must add it to the
  fingerprint when configuration arrives.
- A CI runner with a fresh key never reuses a cache produced elsewhere; that is intended.
- JVM startup and class loading now dominate a cached run; the CLI phase should address them (for example with a
  JDK AOT cache).
