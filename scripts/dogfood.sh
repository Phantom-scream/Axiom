#!/usr/bin/env sh
set -eu

if [ "$#" -lt 3 ]; then
  echo "usage: $0 <owner> <repository> <workflow-run-id> [axiom-base-url]" >&2
  exit 2
fi

owner=$1
repository=$2
run_id=$3
base_url=${4:-http://localhost:8080}

api() {
  if [ -n "${AXIOM_API_KEY:-}" ]; then
    curl --fail --silent --show-error -H "X-Axiom-Api-Key: $AXIOM_API_KEY" "$@"
  else
    curl --fail --silent --show-error "$@"
  fi
}

case "$owner/$repository" in
  *[!a-zA-Z0-9_./-]*) echo "invalid repository identity" >&2; exit 2 ;;
esac
case "$run_id" in
  ''|*[!0-9]*) echo "workflow run id must be numeric" >&2; exit 2 ;;
esac

ingest_response=$(api \
  -H 'Content-Type: application/json' \
  -d "{\"provider\":\"GITHUB_ACTIONS\",\"repositoryOwner\":\"$owner\",\"repositoryName\":\"$repository\",\"externalRunId\":$run_id}" \
  "$base_url/api/v1/pipeline-runs/ingest")

pipeline_id=$(printf '%s' "$ingest_response" | sed -n 's/.*"id":"\([^"]*\)".*/\1/p')
if [ -z "$pipeline_id" ]; then
  echo "ingestion succeeded but no pipeline id could be read" >&2
  exit 1
fi

api -X POST \
  "$base_url/api/v1/pipeline-runs/$pipeline_id/analyze"
printf '\n'
api \
  "$base_url/api/v1/pipeline-runs/$pipeline_id/triage"
printf '\nPipeline run id: %s\n' "$pipeline_id"
