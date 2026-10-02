# Live GitHub acceptance report

Date: 2026-10-02. Baseline: `acdb4681fe7a1955ff00cf56fbe4b7b9273266bb`.
Repository tested: [Phantom-scream/Axiom](https://github.com/Phantom-scream/Axiom).

## Verdict

**PARTIAL acceptance overall; PASS for the exercised read-only backend integration after one fix.**
Real GitHub authentication, workflow/job/log ingestion, analysis orchestration, comparison mapping,
persisted retrieval, bounded historical APIs and idempotency worked. Triage usefulness was limited
for the sampled Spring-context failures: only generic build/exit signals were extracted, classified
UNKNOWN, and no dominant primary or developer actions were returned. No scoring/threshold changes
were made to improve the appearance of this result.

External-write and GitHub-delivered webhook acceptance remain NOT_VALIDATED. This is not a claim
that all live product paths have passed. Mocked coverage is explicitly separate from this campaign.

Status vocabulary: PASS = exercised with the expected result; PARTIAL = exercised with a documented
quality/coverage limitation; FAIL = demonstrated incorrect behavior; NOT_APPLICABLE = prerequisites
do not apply to the selected run; NOT_VALIDATED = not exercised.

## Environment and safety

- Main branch, correct SSH origin, clean starting tree. `.env` is ignored by `.gitignore`; it is not tracked.
- The local fine-grained PAT and operator key were loaded without printing or persisting them.
- Production-profile Compose used real credentials, operator security enabled, and both automatic
  publication toggles explicitly disabled. No repository settings, workflows or PRs were modified.
- PostgreSQL 17 and Docker 28.1.1 were healthy. Public health/readiness returned UP; an operator
  request without the key returned 401, with the key 200. Authenticated Prometheus returned 200.
- Application log checks found no configured credential values. Ingestion logs included pipeline IDs.
- A local Python CA-store problem in the discovery tool was resolved using the installed certifi
  trusted bundle, without disabling TLS. This was a tooling issue, not an Axiom defect.
- No webhook secret, known public HTTPS endpoint, ngrok or cloudflared executable was available.
- Real logs were examined in memory; this report contains only brief evidence excerpts. No raw
  live logs, response archives, credential files or temporary validation scripts were committed.
- The eight real runs and derived results remain in the local acceptance database for inspection.

## Live run registry

All rows are actual GitHub Actions runs, attempt 1, with one persisted job and ten steps each.
The bounded GitHub history listing contained 30 runs and no rerun attempts greater than 1.

| GitHub run ID | Axiom pipeline ID | Source conclusion | PR | Head SHA |
| --- | --- | --- | --- | --- |
| [36775585744](https://github.com/Phantom-scream/Axiom/actions/runs/36775585744) | e9aa92d9-2795-4c76-92ea-6f64fae6967f | SUCCESS | none | acdb4681fe7a1955ff00cf56fbe4b7b9273266bb |
| [36771998597](https://github.com/Phantom-scream/Axiom/actions/runs/36771998597) | cc4b884a-99c7-4272-b789-a16d865bfb16 | SUCCESS | 5 | 6366571f5dc91b254308dc60b79196d945dc80bc |
| [36320555975](https://github.com/Phantom-scream/Axiom/actions/runs/36320555975) | 73b7e185-62a6-4207-9647-23e271e6081a | SUCCESS | none | 7d5ba51149200885c292b195d976dc819c5cc739 |
| [36319285564](https://github.com/Phantom-scream/Axiom/actions/runs/36319285564) | 727f4af7-cf63-4b50-89fd-ccd9a8ad0751 | FAILURE | none | 17c350ef6ab38c929672d36ffe8cdd7b3dcf6a52 |
| [36319107318](https://github.com/Phantom-scream/Axiom/actions/runs/36319107318) | 97496e0a-7027-4eea-9899-1abe69c91bc2 | FAILURE | none | fa4379038711aef01138d6e3bb5d42a145dec982 |
| [36318922992](https://github.com/Phantom-scream/Axiom/actions/runs/36318922992) | b6e2baee-3530-4c07-a116-a5400226e2ac | FAILURE | none | d6b1a863e1907cdffcac6ff60bd24502685a5145 |
| [36318660821](https://github.com/Phantom-scream/Axiom/actions/runs/36318660821) | 1e37ea03-bd47-45ee-95e2-3ff4a2a7c429 | FAILURE | none | c7f057870223741e233bc84a23531f477d930326 |
| [36318463110](https://github.com/Phantom-scream/Axiom/actions/runs/36318463110) | 33d7eccd-5ef3-49e8-95bf-4edfbbbfb071 | FAILURE | none | d8bbbdc7bc9a30bbf202a83bc43bd5d43853581d |

Repository UUID: `b38ae76c-e957-41fb-b748-5524f49dcff2`. All scenario rows below use
Phantom-scream/Axiom and the corresponding pipeline IDs from this registry.

## Acceptance matrix

| Scenario / run | Actual observed failure | Axiom primary classification | Primary useful/correct | Change relevance | Rerun recommendation | Developer actions useful | Publication | Status / notes |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Successful push / 36775585744 | none | no primary | Correctly no fabricated failure | absent: no reliable base | INSUFFICIENT_EVIDENCE, no-triage-needed | N/A: empty | NOT_VALIDATED | PASS; status NO_TRIAGE_NEEDED |
| Successful PR / 36771998597 | none | no primary | Correctly no fabricated failure | N/A: no failures | INSUFFICIENT_EVIDENCE, no-triage-needed | N/A: empty | NOT_VALIDATED | PASS; real comparison and PR #5 metadata |
| Successful push / 36320555975 | none | no primary | Correctly no fabricated failure | absent: no reliable base | INSUFFICIENT_EVIDENCE, no-triage-needed | N/A: empty | NOT_VALIDATED | PASS |
| Failed push / 36319285564 | Spring test-context initialization; missing dependency | UNKNOWN; no primary | Not useful for identifying specific context error | absent: no reliable base | INSUFFICIENT_EVIDENCE | No actions returned | NOT_VALIDATED | PARTIAL quality; backend flow PASS |
| Failed push / 36319107318 | Spring test-context initialization; missing dependency | UNKNOWN; no primary | Not useful for identifying specific context error | absent: no reliable base | INSUFFICIENT_EVIDENCE | No actions returned | NOT_VALIDATED | PARTIAL quality; backend flow PASS |
| Failed push / 36318922992 | Spring test-context initialization; missing dependency | UNKNOWN; no primary | Not useful for identifying specific context error | absent: no reliable base | INSUFFICIENT_EVIDENCE | No actions returned | NOT_VALIDATED | PARTIAL quality; backend flow PASS |
| Failed push / 36318660821 | Spring test-context initialization; missing dependency | UNKNOWN; no primary | Not useful for identifying specific context error | absent: no reliable base | INSUFFICIENT_EVIDENCE | No actions returned | NOT_VALIDATED | PARTIAL quality; backend flow PASS |
| Failed push / 36318463110 | Spring test-context initialization; missing dependency | UNKNOWN; no primary | Not useful for identifying specific context error | absent: no reliable base | INSUFFICIENT_EVIDENCE | No actions returned | NOT_VALIDATED | PARTIAL quality; backend flow PASS |

Short observed GitHub log excerpts shared by the five failed runs:

```text
AxiomApplicationTests > initializationError FAILED
Caused by: org.springframework.beans.factory.UnsatisfiedDependencyException
Caused by: org.springframework.beans.factory.NoSuchBeanDefinitionException
```

These logs show dependency-resolution failures while creating a Spring test context, not evidence
of transient infrastructure or a same-SHA retry recovery. Axiom retained two generic failure events
per failed run and conservatively declined rerun guidance. The run's artifact listing was empty;
no structured test report was ingested, and correlated-test/stability live coverage is NOT_VALIDATED.

## Demonstrated defect and minimal repair

**FAIL before fix:** all eight POST ingestion calls returned 500 even though GitHub reads, metadata
persistence and log storage succeeded. The same pipeline response retrieval failed.

The controller called `ResultSet.getObject(column, Instant.class)` on PostgreSQL `timestamptz`.
The PostgreSQL driver does not support that direct conversion. A new PostgreSQL/MockMvc regression
test reproduced the 500 before the repair and passed afterward. The fix converts
`getTimestamp(column).toInstant()` at the existing response mapper. No models, migrations or
analysis rules changed. The regression checks both GET and POST timestamp serialization.

**PASS after fix:** all eight live re-ingestions returned 201 with the same pipeline identities;
GET retrieval succeeds, and every analysis response is 200 with no FAILED stages.

## Analysis stages and prerequisites

Failed runs: LOG_PROCESSING, DIAGNOSIS and TRIAGE completed on initial analysis; repeated
`recompute=false` reused all three. TEST_CORRELATION and TEST_STABILITY skipped with no reports.
CHANGE_INGESTION skipped NOT_APPLICABLE because these push-run responses did not provide a
trustworthy base; CHANGE_RELEVANCE skipped NO_DATA. Successful runs had no extracted failures,
and persisted triage returned NO_TRIAGE_NEEDED without fabricated primary/actions.

The successful PR run completed real change ingestion and reused it on later analysis. No run had
a failed analysis stage. Diagnosis, triage, ranked failures, actions and rerun-recommendation GETs
were inspected for all five failed runs. UNKNOWN/insufficient output is documented as limited quality,
not converted into an unsupported causal claim or adjusted scoring.

## Real change comparison and relevance

PR #5 supplied base `f25e76e564d2162b0928583e7c37584edd74ef86` and head
`6366571f5dc91b254308dc60b79196d945dc80bc`. Axiom's actual Compare request succeeded.
An independent authenticated GitHub Compare read matched the persisted result:
`.github/workflows/ci.yml`, MODIFIED, CI_CONFIGURATION, module `.github/workflows`, one addition,
one deletion, two changes. No rename was encountered. No branch name was guessed as a base.

For failed run 36319285564, explicit change ingestion correctly returned 422
GIT_COMPARISON_UNAVAILABLE; relevance analysis returned 409 ANALYSIS_PREREQUISITE_MISSING.
Relevance GETs returned empty persisted lists. Explicit relevance analysis on the successful PR
returned 200 with zero analyzed failures and zero outcome counts, as expected. Live relevance scoring with both real failure and
reliable comparison evidence remains NOT_VALIDATED, rather than being advertised as a positive match.

## Idempotency

Live failed run 36319285564 was explicitly ingested again, analyzed with recompute=false, and
recomputed with recompute=true. Counts remained: repository runs 8; selected run jobs 1, stored
logs 1, failure events 2, diagnoses 2, triage results 1. Its pipeline UUID was unchanged. The false
recompute response reported reused failures, diagnoses and triage; true recompute completed
without duplicate rows. Historical evidence on recompute legitimately reflects the ingested sample.

## Historical intelligence and observability

All six repository views returned 200 with days=90/maxRuns=200 (lifecycle days=180), limit=20
where applicable. The repository contains only the eight real ingested runs from this campaign.

- Health: 8 runs, 3 successful, 5 failed, 0 cancelled, success rate 0.375; ten UNKNOWN diagnoses.
- Weekly trends: six runs in the 2026-09-21 bucket (one success/five failures), two successful runs
  in the 2026-09-28 bucket; other requested buckets were empty.
- Incidents: four exact fingerprints, affected-run counts 5, 3, 1, 1. The recurring generic symptoms
  were RESOLVED according to the documented three-later-success rule; this is not proof of a fix.
- Lifecycle: the five-run symptom first seen 2026-09-27T12:18:32Z, last seen 12:33:51Z; main branch.
- Test reliability: valid empty result; no structured executions/snapshots exist in this sample.
- Hotspots: `.github/workflows`, one changed run, no failure association or related failures.

GitHub request metric samples were identical before and after the entire historical GET set,
demonstrating no outbound GitHub requests. Post-restart metrics recorded eight completed analyses
and 24 successful GitHub read requests before the explicit idempotency checks. Tags observed were
fixed application/operation/outcome values, not repository names, fingerprints or credentials.

## External writes and webhook gates

- **PR comments: NOT_VALIDATED.** The five available PRs were active Dependabot updates, not
  disposable integration-test PRs. None was commented on or modified. No comment ID/URL exists.
- **Checks: NOT_VALIDATED.** No live Check was attempted or created. The user described a PAT
  permission/type limitation; no live permission denial was manufactured or inferred as an app bug.
- **GitHub-delivered webhooks: NOT_VALIDATED.** No public endpoint/tunnel or configured webhook
  secret was available. No repository webhook settings were changed and no workflows triggered.
- Same-SHA reruns, flaky/transient failures, live structured-test correlation, renamed-file mapping
  and live publication create/update remain outside this campaign's exercised data.
- Existing mock HTTP/Testcontainers tests remain coverage, not live acceptance evidence.

## Final regression and deployment checks

Baseline: 196 tests, zero failures/errors/skips. Final: **197 tests, zero failures/errors/skips**.
Commands passed: `./gradlew spotlessCheck`, `./gradlew clean test --rerun-tasks`, `./gradlew build`,
`docker compose config --quiet`, `docker build -t axiom:local .`. Focused timestamp regression failed
before repair and passed after. Clean PostgreSQL 17 Testcontainers applied Flyway V1–V14;
no migration was added or changed. The repaired Docker image was used for the successful live run.

No tests were disabled or weakened. `.env` and runtime raw logs are excluded from the commit.
No algorithm tuning, new feature, credential workaround, external publication or repository-setting
change was introduced. The live read-only foundation passes; broad v1 diagnostic quality and live
write/webhook acceptance should not be claimed from this narrow dataset.
