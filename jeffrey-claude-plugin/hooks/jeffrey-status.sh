#!/usr/bin/env bash
# Reports whether the Jeffrey this plugin points at is actually serving.
#
# Every tool in this plugin talks to one HTTP endpoint, and the commonest way for a session to go
# wrong is the dullest: Jeffrey is not running, or is running somewhere else. Without this the model
# finds out by calling a tool and reading a connection error, usually several turns in and often after
# telling the user what it is about to do.
#
# The probe is an MCP initialize against that same endpoint, deliberately: it is the one URL the
# plugin depends on, so a reply means the tools will work and a failure means they will not. An
# endpoint of its own would answer for a server whose MCP support is switched off -- and did, until
# /api/internal/mcp/access/status was removed with the two properties it reported.
#
# Silent when Jeffrey is up: a session that is fine should not open with a status report.

set -uo pipefail

ENDPOINT="${JEFFREY_MCP_ENDPOINT:-http://localhost:8585/api/internal/mcp}"

if ! command -v curl >/dev/null 2>&1; then
  exit 0
fi

INITIALIZE='{"jsonrpc":"2.0","id":0,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"jeffrey-status-hook","version":"1"}}}'

response=$(curl -fsS --max-time 3 \
  -X POST "${ENDPOINT%/}" \
  -H 'Content-Type: application/json' \
  -H 'Accept: application/json, text/event-stream' \
  -d "$INITIALIZE" 2>/dev/null) || {
  cat <<EOF
{"hookSpecificOutput":{"hookEventName":"SessionStart","additionalContext":"Jeffrey is not answering at $ENDPOINT. Every microscope tool talks to that address, so they will all fail until Jeffrey is running there. Start Jeffrey, or point the plugin somewhere else: in Claude Code, /plugin -> microscope -> Jeffrey MCP endpoint. Do not retry the tools in the meantime -- tell the user."}}
EOF
  exit 0
}

# Answering, but not with a JSON-RPC result: something is on that address and it is not Jeffrey's MCP
# server. Worth saying, because the tools will fail in a way that looks like a protocol bug.
case "$response" in
  *'"result"'*) ;;
  *)
    cat <<EOF
{"hookSpecificOutput":{"hookEventName":"SessionStart","additionalContext":"Something is answering at $ENDPOINT but it is not a Jeffrey MCP server -- an initialize request came back without a JSON-RPC result. The microscope tools will fail. Check the endpoint: in Claude Code, /plugin -> microscope -> Jeffrey MCP endpoint."}}
EOF
    ;;
esac

exit 0
