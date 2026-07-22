#!/bin/sh
#
# Jeffrey JIB entrypoint wrapper.
#
# Runs `provisioner init` to populate /tmp/jvm.args, then exec's the command JIB originally
# produced (java -cp <classpath> <MainClass>, or java -jar <app.jar>, etc.) with the argfile
# inserted right after the java binary.
#
# Runtime kill switch:
#   JEFFREY_ENABLED  set to false|0|no|off (case-insensitive) to bypass profiling entirely
#                    and exec the JIB command verbatim — no provisioner init, no argfile.
#
# All paths are overridable via environment variables:
#   JEFFREY_HOME             required (unless JEFFREY_PROVISIONER_PATH is set directly)
#   JEFFREY_PROVISIONER_PATH default ${JEFFREY_HOME}/libs/current/provisioner-<arch>
#   JEFFREY_BASE_CONFIG   default /jeffrey/jeffrey-base.conf (optional — without the file,
#                         the provisioner configures itself from JEFFREY_* env vars such as
#                         JEFFREY_PROJECT_NAME and JEFFREY_WORKSPACE_REF_ID)
#   JEFFREY_OVERRIDE_CONFIG default /jeffrey/jeffrey-overrides.conf (optional)
#   JEFFREY_ARG_FILE      default /tmp/jvm.args
#   JEFFREY_WAIT_FOR_LIBS seconds to wait for the shared volume to contain the provisioner
#                         binary (copy-libs startup ordering); default 0
#
# Fail-open guarantee: any misconfiguration (missing binaries, broken config, failed init)
# starts the application WITHOUT profiling instead of preventing it from starting.

set -e

# Runtime kill switch — bypass everything and exec the JIB command as-is.
case "${JEFFREY_ENABLED:-true}" in
  false|0|no|off|FALSE|NO|OFF|False|No|Off)
    if [ $# -eq 0 ]; then
      echo "jeffrey-jib: JEFFREY_ENABLED=${JEFFREY_ENABLED} but no command provided" >&2
      exit 1
    fi
    exec "$@"
    ;;
esac

# If the caller hasn't told us where the provisioner lives, log a warning and continue without
# profiling — the application must keep booting even when Jeffrey is not configured.
if [ -z "${JEFFREY_PROVISIONER_PATH:-}" ] && [ -z "${JEFFREY_HOME:-}" ]; then
  echo "jeffrey-jib: neither JEFFREY_HOME nor JEFFREY_PROVISIONER_PATH is set — Jeffrey is disabled, starting application without profiling." >&2
  echo "jeffrey-jib: to enable, set JEFFREY_HOME to the shared volume root (provisioner at \${JEFFREY_HOME}/libs/current/provisioner-<arch>)," >&2
  echo "jeffrey-jib: or JEFFREY_PROVISIONER_PATH directly to the provisioner binary." >&2
  if [ $# -eq 0 ]; then
    echo "jeffrey-jib: no command provided; cannot start the application." >&2
    exit 1
  fi
  exec "$@"
fi

case "$(uname -m)" in
  x86_64)  ARCH="amd64" ;;
  aarch64) ARCH="arm64" ;;
  *)       ARCH="$(uname -m)" ;;
esac

# Expand the literal {arch} placeholder in JEFFREY_* env vars baked by jeffrey-jib
# (or set on the pod) so multi-arch images can ship a single static value such as
# /jeffrey-libs/libasyncProfiler-{arch}.so without per-arch builds.
for var in JEFFREY_PROVISIONER_PATH JEFFREY_PROFILER_PATH JEFFREY_AGENT_PATH; do
  eval "value=\${$var-}"
  case "$value" in
    *"{arch}"*)
      expanded=$(printf '%s' "$value" | sed "s/{arch}/${ARCH}/g")
      eval "export $var=\"\$expanded\""
      ;;
  esac
done

PROVISIONER="${JEFFREY_PROVISIONER_PATH:-${JEFFREY_HOME}/libs/current/provisioner-${ARCH}}"

# Optionally wait for the shared volume to be populated by the hub's copy-libs — covers
# the ordering race when application pods start before jeffrey-hub on a fresh cluster.
WAIT_SECS="${JEFFREY_WAIT_FOR_LIBS:-0}"
waited=0
while [ ! -x "$PROVISIONER" ] && [ "$waited" -lt "$WAIT_SECS" ]; do
  echo "jeffrey-jib: waiting for provisioner on the shared volume (${waited}/${WAIT_SECS}s): $PROVISIONER" >&2
  sleep 2
  waited=$((waited + 2))
done

if [ $# -eq 0 ]; then
  echo "jeffrey-jib: no command provided (JIB CMD missing); cannot launch JVM." >&2
  exit 1
fi

# Fail-open: a missing provisioner binary must never stop the application.
if [ ! -x "$PROVISIONER" ]; then
  echo "jeffrey-jib: profiling DISABLED: provisioner not found or not executable: $PROVISIONER" >&2
  echo "jeffrey-jib: is jeffrey-hub running with copy-libs enabled and the shared volume mounted at JEFFREY_HOME?" >&2
  exec "$@"
fi

BASE_CONFIG="${JEFFREY_BASE_CONFIG:-/jeffrey/jeffrey-base.conf}"
OVERRIDE_CONFIG="${JEFFREY_OVERRIDE_CONFIG:-/jeffrey/jeffrey-overrides.conf}"
ARG_FILE="${JEFFREY_ARG_FILE:-/tmp/jvm.args}"

# Make sure the argfile check below reflects THIS run, not a leftover file.
rm -f "$ARG_FILE"

# The config file is optional: when absent, the provisioner configures itself from
# JEFFREY_* environment variables (JEFFREY_PROJECT_NAME, JEFFREY_WORKSPACE_REF_ID, ...).
INIT_OK=true
if [ -f "$BASE_CONFIG" ]; then
  if [ -f "$OVERRIDE_CONFIG" ]; then
    "$PROVISIONER" init --base-config="$BASE_CONFIG" --override-config="$OVERRIDE_CONFIG" || INIT_OK=false
  else
    "$PROVISIONER" init --base-config="$BASE_CONFIG" || INIT_OK=false
  fi
else
  "$PROVISIONER" init || INIT_OK=false
fi

# Fail-open: a failed init (or an init that produced no argfile) starts the
# application without profiling instead of pointing the JVM at a missing argfile.
if [ "$INIT_OK" != "true" ] || [ ! -f "$ARG_FILE" ]; then
  echo "jeffrey-jib: profiling DISABLED: provisioner init failed or produced no argfile (${ARG_FILE}); starting application without profiling." >&2
  exec "$@"
fi

# JIB CMD is ["java","-cp","@/app/jib-classpath-file","<MainClass>"] or ["java","-jar",…].
# Insert @argfile right after the java binary — preserves the flag ordering of the pre-
# jeffrey-jib `java @/tmp/jvm.args -cp … MainClass` pattern, without the JDK_JAVA_OPTIONS
# "Picked up …" startup noise.
JAVA_BIN="$1"
shift
exec "$JAVA_BIN" "@${ARG_FILE}" "$@"
