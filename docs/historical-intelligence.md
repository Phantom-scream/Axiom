# Historical intelligence

These views read persisted cross-run evidence only. They never call GitHub, rerun analysis, forecast
outcomes, infer authors/teams, or establish causality. All dates and counts are scoped to the returned
`window`: date cutoff AND most-recent run cap. `firstSeenAt` is first observed **within that sample**,
not a claim about the lifetime of a repository. Missing analysis remains missing evidence.

## Endpoints

| GET `/api/v1/repositories/{repositoryId}` suffix | Defaults | Meaning |
| --- | --- | --- |
| `/trends` | `days=90&granularity=WEEK&maxRuns=500` | UTC DAY or Monday-start WEEK buckets |
| `/incidents` | `days=90&limit=20&maxRuns=500` | Separate deterministic fingerprint groups |
| `/failures/{fingerprint}/lifecycle` | `days=180&maxRuns=500` | One fingerprint's observed lifecycle |
| `/tests/reliability` | `days=90&limit=20&maxRuns=500` | Stored unstable-test labels plus window counts |
| `/hotspots` | `days=90&limit=20&maxRuns=500` | Module/category historical associations |

Bounds default to 730 days, 1000 runs and 100 returned groups; invalid values return 400, missing
repositories 404. Empty repositories return empty groups, zero-filled trend buckets, or an UNKNOWN
lifecycle with zero counts. Requests require the operator key when security is enabled.

## Lifecycle and incidents

Fingerprint identity is exact; different fingerprints are never semantically clustered. Log occurrence
counts sum persisted `occurrence_count`; affected counts deduplicate pipeline attempts. Classification
is the latest stored classifier-v1 diagnosis, otherwise UNKNOWN. Branch output is capped at 20.
Incident ranking is occurrence count descending, last seen descending, then fingerprint.

Lifecycle precedence and defaults:

1. RESOLVED: at least two affected runs, followed by at least three observed clean completed runs.
   A clean run is successful, or has persisted triage and no matching signal. Unprocessed failed runs
   do not establish absence. This does not identify a fixing commit.
2. INTERMITTENT: same-SHA adjacent-attempt recovery to success, or repeated occurrences with a
   successful run between them.
3. ACTIVE: repeated occurrences and a matching failed/timed-out run among the five most recent runs.
4. UNKNOWN: otherwise; a single observation is not forced into ACTIVE/RESOLVED.

Thresholds are centralized in `axiom.history`. Recovery counts deduplicate affected attempts and use
adjacent attempts within the same external run and selected repository sample. Common test/category/
module values are the most frequent persisted associations, with deterministic lexical tie breaks.
Related/unrelated incident counts are distinct run counts using persisted relevance-v1 results.

## Trend semantics

Success rate is successes / (successes + failures + timed-out runs). Cancelled and unfinished/unknown
outcomes are excluded; an empty applicable denominator returns 0.0. Pipeline attempts count as runs.
First and last buckets may be partial. Empty UTC buckets are returned.

New fingerprints first appear in that bucket **within the selected sample**. Recurring fingerprints
were observed earlier in the sample or affect multiple runs in the same bucket. A fingerprint can be
both new and recurring within one bucket. These are not lifetime novelty guarantees.
Classification and relevance counts deduplicate `(pipeline run, fingerprint)`; rerun counts use
persisted triage-v1. Missing diagnoses are not invented.

## Tests and hotspots

Test views use stable IDs and persisted stability-v1 snapshots, without global recalculation. Snapshot
labels are the currently stored labels; execution/pass/failure and first/last failure counts describe
the selected window. ERROR is included in failure counts. Same-SHA recovery uses the existing
`RerunAnalysisService` in one bounded batch. FLAKY, SUSPECTED_FLAKY and CONSISTENTLY_FAILING remain
separate. Ranking is failure count descending, then stable ID; a global top-N may exclude other labels.

Hotspots count changed runs per module, failed/timed-out changed runs, and distinct related
run/fingerprint signals from persisted related-file links. Missing module hints use category groups.
Top classifications and at most ten fingerprints come only from those related signals. A failed run
with changes is an association, not evidence that the module caused the failure. SQL aggregation uses
a fixed query count, not one repository lookup per module.
