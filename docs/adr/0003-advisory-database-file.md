# ADR-0003: Advisory database as one compact file, fed from OSV

- **Status:** Accepted
- **Date:** 2026-10-21
- **Deciders:** project maintainers

## Context

SPEC §8 asks for a local SQLite or H2 database in `~/.spring-inspector/advisories`, an incremental, checksummed
`update` that works behind a proxy, and data from OSV.dev, GitHub Security Advisories and the Spring advisories page.

The database is read on every `check`, including the pre-commit hook, where the whole run should stay well under
5 seconds. Measurements on the real data (October 2026):

- OSV's Maven archive, `Maven/all.zip`, is about 10 MB and holds 7,197 advisories, 7,084 of them not withdrawn.
- OSV already includes the GitHub Security Advisories for Maven (`GHSA-*` ids, with CVE aliases) and the Spring
  advisories, which are published through GitHub.
- OSV's incremental index (`modified_id.csv`) is not available from every region, but the archive carries an ETag
  and an MD5 (`x-goog-hash`).

## Decision

- **Source:** only OSV's Maven archive. It covers GHSA and Spring advisories, so `update` makes one request.
- **Incremental update:** a conditional download with `If-None-Match` and the stored ETag. An unchanged archive
  costs one empty response, and nothing about the project is sent.
- **Integrity:** the body must match the published MD5. The archive is read as untrusted data: entries count, size
  and expanded total are bounded, and a download that holds no advisory documents is refused, so an error page can
  never replace good data. HTTPS protects the transfer itself.
- **Proxy:** `HTTPS_PROXY`, otherwise Java's proxy settings.
- **Storage:** one binary file, `osv-maven.bin`, holding only what matching needs: ids, aliases, summary, severity,
  and per package the version ranges or listed versions. It has a string table and a CRC32, is written to a
  temporary file and moved into place, and reads as missing when damaged or of another format version. For the
  current data it is 1.4 MB and loads in 10–60 ms. Neither SQLite nor H2 is used.

## Consequences

- No database engine or native library on the class path, and no schema migrations: a new format version just asks
  for `update`.
- Matching is in memory, indexed by `groupId:artifactId`. That is fast for one project's few hundred dependencies,
  but there is no ad-hoc query language.
- Advisories that only appear outside OSV are missed until OSV picks them up.
- The data stays under its publishers' licences (GitHub Advisory Database: CC-BY 4.0). It is downloaded by each
  user and is not shipped with Spring Inspector, and findings link back to the advisory at osv.dev.
