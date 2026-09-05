#!/usr/bin/env bash
# Reports whether the Jeffrey this plugin points at is actually serving.
#
# Every tool in this plugin talks to one HTTP endpoint, and the commonest way for a session to go
# wrong is the dullest: Jeffrey is not running, or is running somewhere else. Without this the model
# finds out by calling a tool and reading a connection error, usually several turns in and often after
# telling the user what it is about to do.
#
# Silent when Jeffrey is up: a session that is fine should not open with a status report.

set -uo pipefail

ENDPOINT="${JEFFREY_MCP_ENDPOINT:-http://localhost:8585/api/internal/mcp}"
STATUS_URL="${ENDPOINT%/}/access/status"

if ! command -v curl >/dev/null 2>&1; then
  exit 0
fi

response=$(curl -fsS --max-time 3 "$STATUS_URL" 2>/dev/null) || {
  cat <<EOF
{"hookSpecificOutput":{"hookEventName":"SessionStart","additionalContext":"Jeffrey is not answering at $ENDPOINT. Every microscope tool talks to that address, so they will all fail until Jeffrey is running there. Start Jeffrey, or point the plugin somewhere else: in Claude Code, /plugin -> microscope -> Jeffrey MCP endpoint. Do not retry the tools in the meantime -- tell the user."}}
EOF
  exit 0
}

# Serving, but with families switched off the model would otherwise discover by their absence.
extras=""
case "$response" in
  *'"ingestEnabled":false'*) extras="$extras Recording ingestion is off, so recordings_ tools are not available and a .jfr file has to be uploaded in the UI." ;;
esac
case "$response" in
  *'"computeEnabled":false'*) extras="$extras Compute is off, so heap_prepare is unavailable and heap reports have to be run from the Jeffrey UI." ;;
esac

if [ -n "$extras" ]; then
  printf '{"hookSpecificOutput":{"hookEventName":"SessionStart","additionalContext":"Jeffrey is serving at %s.%s"}}\n' "$ENDPOINT" "$extras"
fi

exit 0
