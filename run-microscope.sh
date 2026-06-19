#!/usr/bin/env bash
#
# Launch Jeffrey Microscope under async-profiler (span-api build), self-profiling into a JFR that
# also contains the application's own profiler.Span events (http.*, flamegraph.generate, ai.*.call, ...).
#
# Events: cpu + wall + alloc + lock, merged with the JVM's JFR (jfrsync=profile) into one .jfr.
# Output: jeffrey-<timestamp>.jfr in the repo root (%t = async-profiler timestamp placeholder).
#
# Method tracing: async-profiler instruments a curated set of Jeffrey's own per-operation methods
# (REST entry points, flamegraph/timeseries managers, recording ingestion, DuckDB queries) and
# records each invocation's wall-clock duration + stack as a jdk.MethodTrace event, which Jeffrey
# surfaces on the "Method Traces" flamegraph card. So just clicking around Microscope (open a
# flamegraph/timeseries, ingest a recording) produces method traces you can then analyse in Jeffrey.
# Override the set with TRACE="pkg.Class.method[:threshold] ..." (space/comma separated), or set
# TRACE=off to disable method tracing.
#
# perf_events CPU sampling needs relaxed kernel limits, so for event=cpu this script automatically sets:
#     kernel.perf_event_paranoid = 1
#     kernel.kptr_restrict       = 0
# Setting those requires root, so run it with sudo:
#     sudo ./run-microscope-profiled.sh
# (Use EVENT=itimer to sample CPU without perf — then no kernel changes and no sudo are required.)
#
# NOTE: with sudo the JVM runs as root and uses root's home for Jeffrey data; your own ~/.jeffrey is
# left untouched. Pass EVENT=itimer to run as your normal user instead.
#
# Pass --clean to wipe the Jeffrey data dir (${HOME}/.jeffrey-microscope — with sudo that is
# /root/.jeffrey-microscope) before launch, so the run starts from clean data. All other arguments
# are forwarded to the application.
#
# Local-only helper — gitignored, not committed.
#
set -euo pipefail

# Parse our own flags (e.g. --clean) out of the args; everything else is forwarded to the jar.
CLEAN=0
REST=()
while [[ $# -gt 0 ]]; do
    case "$1" in
        --clean) CLEAN=1; shift ;;
        *)       REST+=("$1"); shift ;;
    esac
done
set -- "${REST[@]+"${REST[@]}"}"

REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

ASPROF="${ASPROF:-/home/pbouda/IdeaProjects/async-profiler/build/lib/libasyncProfiler.so}"
EVENT="${EVENT:-cpu}"
JAR="${REPO_DIR}/build/build-microscope/target/microscope.jar"
# Jeffrey data dir, derived from $HOME exactly as the JVM derives user.home (so --clean wipes the
# very dir the app will use). With sudo: /root/.jeffrey-microscope; as your user: ~/.jeffrey-microscope.
DATA_DIR="${HOME}/.jeffrey-microscope"
# jfrsync points at a custom .jfc (generated below from the JDK profile with virtual-thread events
# enabled), so the VT events are merged straight into async-profiler's jeffrey-%t.jfr output.
AGENT_OPTS_BASE="start,event=${EVENT},wall,alloc,lock,file=${REPO_DIR}/jeffrey-%t.jfr"

if [[ ! -f "${ASPROF}" ]]; then
    echo "error: libasyncProfiler.so not found at ${ASPROF}" >&2
    echo "       build it: (cd /home/pbouda/IdeaProjects/async-profiler && make build/lib/libasyncProfiler.so)" >&2
    exit 1
fi
if [[ ! -f "${JAR}" ]]; then
    echo "error: microscope.jar not found at ${JAR}" >&2
    echo "       build it: mvn -pl build/build-microscope -am package" >&2
    exit 1
fi

# perf_events CPU sampling requires relaxed kernel limits → set them automatically (needs root).
if [[ "${EVENT}" == "cpu" ]]; then
    if [[ "${EUID}" -ne 0 ]]; then
        echo "error: event=cpu sets kernel perf limits and must be run with sudo:" >&2
        echo "       sudo ${BASH_SOURCE[0]}" >&2
        echo "       (or run without sudo using software sampling: EVENT=itimer ${BASH_SOURCE[0]})" >&2
        exit 1
    fi
    echo "Relaxing kernel perf limits for perf_events CPU sampling:"
    sysctl -w kernel.perf_event_paranoid=1
    sysctl -w kernel.kptr_restrict=0
    echo
fi

# Resolve the java binary. sudo resets PATH (dropping SDKMAN), so fall back to JAVA_HOME, then the
# invoking user's SDKMAN 'current', then PATH.
JAVA_BIN="${JAVA_BIN:-}"
if [[ -z "${JAVA_BIN}" && -n "${JAVA_HOME:-}" && -x "${JAVA_HOME}/bin/java" ]]; then
    JAVA_BIN="${JAVA_HOME}/bin/java"
fi
if [[ -z "${JAVA_BIN}" && -n "${SUDO_USER:-}" ]]; then
    user_home="$(getent passwd "${SUDO_USER}" | cut -d: -f6)"
    candidate="${user_home}/.sdkman/candidates/java/current/bin/java"
    [[ -x "${candidate}" ]] && JAVA_BIN="${candidate}"
fi
if [[ -z "${JAVA_BIN}" ]] && command -v java >/dev/null 2>&1; then
    JAVA_BIN="$(command -v java)"
fi
if [[ -z "${JAVA_BIN}" ]]; then
    echo "error: could not locate a 'java' binary (sudo resets PATH/JAVA_HOME)." >&2
    echo "       run as:  sudo -E ${BASH_SOURCE[0]}                       # preserves your JAVA_HOME/PATH" >&2
    echo "       or:      sudo JAVA_BIN=\"\$(command -v java)\" ${BASH_SOURCE[0]}" >&2
    exit 1
fi

# Build a custom JFR config from the JDK's profile.jfc with virtual-thread events enabled, so the VT
# events are merged into async-profiler's output via jfrsync (no separate JVM recording needed):
#   - jdk.VirtualThreadStart / jdk.VirtualThreadEnd : enabled false -> true
#   - jdk.VirtualThreadPinned                       : threshold 20 ms -> 0 ms (catch every pinning)
#   - jdk.VirtualThreadSubmitFailed                 : already enabled in the profile preset
#   - jdk.OldObjectSample                           : enabled false -> true (memory leak candidates)
#   - jdk.CPUTimeSample                             : enabled false -> true (JEP 509 CPU-time profiling)
PROFILE_JFC="$(dirname "$(dirname "${JAVA_BIN}")")/lib/jfr/profile.jfc"
if [[ ! -f "${PROFILE_JFC}" ]]; then
    echo "error: profile.jfc not found at ${PROFILE_JFC}" >&2
    exit 1
fi
VT_JFC="$(mktemp "${TMPDIR:-/tmp}/jeffrey-asprof-vt.XXXXXX.jfc")"
trap 'rm -f "${VT_JFC}"' EXIT
awk '
    /<event name="jdk\.VirtualThreadStart">/        { ev = "enable" }
    /<event name="jdk\.VirtualThreadEnd">/          { ev = "enable" }
    /<event name="jdk\.VirtualThreadSubmitFailed">/ { ev = "enable" }
    /<event name="jdk\.OldObjectSample">/           { ev = "enable" }
    /<event name="jdk\.CPUTimeSample">/             { ev = "enable" }
    /<event name="jdk\.VirtualThreadPinned">/       { ev = "pin" }
    {
        if (ev == "enable") {
            sub(/<setting name="enabled">false<\/setting>/, "<setting name=\"enabled\">true</setting>")
        }
        if (ev == "pin") {
            sub(/<setting name="threshold">20 ms<\/setting>/, "<setting name=\"threshold\">0 ms</setting>")
        }
        print
        if ($0 ~ /<\/event>/) { ev = "" }
    }
' "${PROFILE_JFC}" > "${VT_JFC}"

# Curated method-trace targets — moderate-frequency "unit of work" methods (one per UI action / per
# recording), so the resulting jdk.MethodTrace events stay meaningful instead of flooding. DuckDB's
# query() is many-per-request, so it carries a 1ms latency threshold to keep only the slow queries.
DEFAULT_TRACE_METHODS=(
    # REST entry points — one event per flamegraph/timeseries view
    cafe.jeffrey.microscope.core.web.controllers.profile.FlamegraphController.generate
    cafe.jeffrey.microscope.core.web.controllers.profile.TimeseriesController.generate
    # Managers / providers behind the controllers — see where the time inside a request goes
    cafe.jeffrey.profile.manager.PrimaryFlamegraphManager.generate
    cafe.jeffrey.profile.manager.PrimaryTimeseriesManager.timeseries
    cafe.jeffrey.flamegraph.provider.FlamegraphDataProvider.provideFrame
    cafe.jeffrey.flamegraph.FlameGraphProtoBuilder.build
    # Recording ingestion — one event per parsed recording
    cafe.jeffrey.profile.parser.JfrRecordingEventParser.start
    # DuckDB query chokepoint — many per request, so only trace calls slower than 1ms
    cafe.jeffrey.shared.persistence.client.DatabaseClient.query:1ms
)

# TRACE env override: "off" disables tracing; a non-empty value replaces the defaults (split on
# whitespace and commas); unset uses the curated defaults above.
if [[ "${TRACE:-}" == "off" ]]; then
    TRACE_METHODS=()
elif [[ -n "${TRACE:-}" ]]; then
    IFS=', ' read -r -a TRACE_METHODS <<< "${TRACE}"
else
    TRACE_METHODS=("${DEFAULT_TRACE_METHODS[@]}")
fi

TRACE_OPTS=""
for trace_method in ${TRACE_METHODS[@]+"${TRACE_METHODS[@]}"}; do
    TRACE_OPTS+=",trace=${trace_method}"
done

AGENT_OPTS="${AGENT_OPTS_BASE},jfrsync=${VT_JFC}${TRACE_OPTS}"

# --clean: wipe the data dir for a fresh-data run. Guard against a misresolved/empty path so a bad
# expansion can never turn this into a catastrophic rm.
if [[ "${CLEAN}" -eq 1 ]]; then
    if [[ -z "${DATA_DIR}" || "${DATA_DIR}" != *"/.jeffrey-microscope" ]]; then
        echo "error: refusing to clean unexpected data dir: '${DATA_DIR}'" >&2
        exit 1
    fi
    echo "Cleaning data dir (--clean): ${DATA_DIR}"
    rm -rf "${DATA_DIR}"
    echo
fi

echo "Starting microscope with async-profiler agent:"
echo "  java  : ${JAVA_BIN}"
echo "  agent : -agentpath:${ASPROF}=${AGENT_OPTS}"
echo "  jfc   : ${VT_JFC} (profile.jfc + virtual-thread events)"
echo "  jar   : ${JAR}"
if [[ "${#TRACE_METHODS[@]}" -gt 0 ]]; then
    echo "  trace : ${#TRACE_METHODS[@]} method(s) -> jdk.MethodTrace (Jeffrey 'Method Traces' card)"
    for trace_method in "${TRACE_METHODS[@]}"; do
        echo "            - ${trace_method}"
    done
else
    echo "  trace : disabled (TRACE=off)"
fi
echo

exec "${JAVA_BIN}" "-agentpath:${ASPROF}=${AGENT_OPTS}" -XX:NativeMemoryTracking=summary -jar "${JAR}" "$@"
