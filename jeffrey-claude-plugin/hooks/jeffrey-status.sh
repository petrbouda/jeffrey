#!/usr/bin/env bash
# Jeffrey
# Copyright (C) 2026 Petr Bouda
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# Reports whether the Jeffrey this plugin points at is actually serving.
#
# Every tool in this plugin talks to one HTTP endpoint, and the commonest way for a session to go
# wrong is the dullest: Jeffrey is not running, or is running somewhere else. Without this the model
# finds out by calling a tool and reading a connection error, usually several turns in and often after
# telling the user what it is about to do.
#
# The probe is an MCP server/discover against that same endpoint, deliberately: it is the one URL the
# plugin depends on, so a reply means the tools will work and a failure means they will not. An
# endpoint of its own would answer for a server whose MCP support is switched off -- and did, until
# the separate access-status endpoint was removed with the two properties it reported.
#
# Jeffrey speaks MCP 2026-07-28 only, so the probe is shaped the way every request to it must be: the
# version and the client capabilities in params._meta, and the MCP-Protocol-Version and Mcp-Method
# headers repeating them. server/discover is the one method that never assembles the toolset, so the
# probe stays cheap however many profiles Jeffrey holds. Success is a JSON-RPC result that names
# supportedVersions.
#
# Silent when Jeffrey is up: a session that is fine should not open with a status report.

set -uo pipefail

# The endpoint the reader configured, then their own override, then the default. The first is what
# `/plugin` writes for this plugin's endpoint_url setting: Claude Code exports every user config
# value as CLAUDE_PLUGIN_OPTION_<KEY>, which is how a shell-form hook reads one (${user_config.*}
# interpolates in exec form only). Without it a reader who moved Jeffrey to another port got working
# tools and a hook still probing 8585, which opened every session by announcing that the server they
# were about to use was not answering.
#
# JEFFREY_MCP_ENDPOINT is the second on purpose rather than as an afterthought: it is the env var the
# Gemini CLI extension declares as its setting, so a Gemini reader who moved Jeffrey is answered by
# the same line, and anyone else can export it.
ENDPOINT="${CLAUDE_PLUGIN_OPTION_ENDPOINT_URL:-${JEFFREY_MCP_ENDPOINT:-http://localhost:8585/api/mcp}}"

if ! command -v curl >/dev/null 2>&1; then
  exit 0
fi

PROTOCOL_VERSION='2026-07-28'
DISCOVER_METHOD='server/discover'
DISCOVER='{"jsonrpc":"2.0","id":0,"method":"'"$DISCOVER_METHOD"'","params":{"_meta":{"io.modelcontextprotocol/protocolVersion":"'"$PROTOCOL_VERSION"'","io.modelcontextprotocol/clientCapabilities":{},"io.modelcontextprotocol/clientInfo":{"name":"jeffrey-status-hook","version":"1"}}}}'

# The token, when the reader set one, from the same place the tools take theirs. Under Claude Code
# that is only the plugin's token setting (CLAUDE_PLUGIN_OPTION_TOKEN): the plugin's server sends
# ${user_config.token} and nothing else, so falling back to JEFFREY_MCP_TOKEN there would pass the
# check while every tool is refused. Claude Code is recognised by CLAUDE_PLUGIN_ROOT or by any
# CLAUDE_PLUGIN_OPTION_* it exported. Everywhere else -- Gemini, or the script run by hand -- it is
# JEFFREY_MCP_TOKEN, the variable the Gemini manifest and a Codex bearer_token_env_var read. Sent
# only when set; a Jeffrey without jeffrey.microscope.mcp.token ignores the header anyway.
if [ -n "${CLAUDE_PLUGIN_ROOT:-}" ] || [ -n "${!CLAUDE_PLUGIN_OPTION_*}" ]; then
  UNDER_CLAUDE_CODE=1
  TOKEN="${CLAUDE_PLUGIN_OPTION_TOKEN:-}"
else
  UNDER_CLAUDE_CODE=0
  TOKEN="${JEFFREY_MCP_TOKEN:-}"
fi

# Writes the one SessionStart message this hook ever has to say. The text is JSON-escaped here, since
# a 403's body is quoted into it verbatim.
report() {
  escaped=$(printf '%s' "$1" | sed -e 's/\\/\\\\/g' -e 's/"/\\"/g' | tr '\n\r\t' '   ')
  printf '{"hookSpecificOutput":{"hookEventName":"SessionStart","additionalContext":"%s"}}\n' "$escaped"
}

# Status and body separately, so a refusal can be told from a server that is not there. No -f: a
# 401 or 403 is an answer worth reading, not a failure to discard. curl reports 000 when nothing
# answered at all.
BODY_FILE=$(mktemp 2>/dev/null) || exit 0
trap 'rm -f "$BODY_FILE"' EXIT

set -- -sS --max-time 3 -o "$BODY_FILE" -w '%{http_code}' \
  -X POST "${ENDPOINT%/}" \
  -H 'Content-Type: application/json' \
  -H 'Accept: application/json, text/event-stream' \
  -H "MCP-Protocol-Version: $PROTOCOL_VERSION" \
  -H "Mcp-Method: $DISCOVER_METHOD" \
  -d "$DISCOVER"
if [ -n "$TOKEN" ]; then
  set -- "$@" -H "Authorization: Bearer $TOKEN"
fi
status=$(curl "$@" 2>/dev/null)
status="${status:-000}"
body=$(cat "$BODY_FILE" 2>/dev/null)

WHERE_TO_POINT="in Claude Code, /plugin -> microscope -> Jeffrey MCP endpoint; in Gemini CLI, the extension's Jeffrey MCP endpoint setting, or JEFFREY_MCP_ENDPOINT in the environment"

# Where the token is set, for the client this session runs in. The same wording as Jeffrey's own
# 401 text, narrowed to this client when the hook knows which one it is.
if [ "$UNDER_CLAUDE_CODE" = 1 ]; then
  WHERE_THE_TOKEN_GOES="Claude Code: the plugin's Jeffrey MCP token setting (/plugin -> microscope)"
else
  WHERE_THE_TOKEN_GOES="Codex / Gemini / other clients: JEFFREY_MCP_TOKEN (Codex: bearer_token_env_var)"
fi

case "$status" in
  000)
    report "Jeffrey is not answering at $ENDPOINT. Every microscope tool talks to that address, so they will all fail until Jeffrey is running there. Start Jeffrey, or point the plugin somewhere else: $WHERE_TO_POINT. Do not retry the tools in the meantime -- tell the user."
    ;;
  401)
    if [ -n "$TOKEN" ]; then
      problem="refused the token this session sends (HTTP 401): it does not match jeffrey.microscope.mcp.token"
    else
      problem="requires a bearer token (HTTP 401) and this session sends none"
    fi
    report "Jeffrey at $ENDPOINT $problem. Every microscope tool will be refused until it matches. Set the token Jeffrey is configured with -- $WHERE_THE_TOKEN_GOES -- and restart the session. Do not retry the tools in the meantime -- tell the user."
    ;;
  403)
    # The server's own sentence names the check and the property that fixes it.
    reason=$(printf '%s' "$body" \
      | sed -n 's/^.*"error"[[:space:]]*:[[:space:]]*"\(.*\)"[[:space:]]*}[[:space:]]*$/\1/p' \
      | sed -e 's/\\"/"/g' -e 's/\\\\/\\/g')
    report "Jeffrey at $ENDPOINT refused this session (HTTP 403): ${reason:-No reason given.} Every microscope tool will be refused the same way until Jeffrey's configuration changes. Do not retry the tools in the meantime -- tell the user."
    ;;
  2??)
    # Answering, but not with a discover result: something is on that address and it is not
    # Jeffrey's MCP server, or not one that speaks MCP 2026-07-28. Worth saying, because the tools
    # will fail in a way that looks like a protocol bug.
    case "$body" in
      *'"result"'*'"supportedVersions"'* | *'"supportedVersions"'*'"result"'*) ;;
      *)
        report "Something is answering at $ENDPOINT but it is not a Jeffrey MCP server -- a server/discover request (MCP $PROTOCOL_VERSION) came back without a discover result. The microscope tools will fail. Check the endpoint: $WHERE_TO_POINT."
        ;;
    esac
    ;;
  *)
    report "Something is answering at $ENDPOINT but it is not a Jeffrey MCP server -- a server/discover request (MCP $PROTOCOL_VERSION) came back with HTTP $status. The microscope tools will fail. Check the endpoint: $WHERE_TO_POINT."
    ;;
esac

exit 0
