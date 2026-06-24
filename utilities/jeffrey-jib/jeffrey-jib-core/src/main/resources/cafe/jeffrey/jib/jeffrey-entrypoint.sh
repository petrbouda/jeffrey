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
#   JEFFREY_BASE_CONFIG   default /jeffrey/jeffrey-base.conf
#   JEFFREY_OVERRIDE_CONFIG default /jeffrey/jeffrey-overrides.conf (optional)
#   JEFFREY_ARG_FILE      default /tmp/jvm.args

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
if [ ! -x "$PROVISIONER" ]; then
  echo "jeffrey-jib: provisioner not found or not executable: $PROVISIONER" >&2
  echo "jeffrey-jib: set JEFFREY_HOME to the shared volume, or JEFFREY_PROVISIONER_PATH directly." >&2
  exit 1
fi

BASE_CONFIG="${JEFFREY_BASE_CONFIG:-/jeffrey/jeffrey-base.conf}"
OVERRIDE_CONFIG="${JEFFREY_OVERRIDE_CONFIG:-/jeffrey/jeffrey-overrides.conf}"
ARG_FILE="${JEFFREY_ARG_FILE:-/tmp/jvm.args}"

if [ -f "$OVERRIDE_CONFIG" ]; then
  "$PROVISIONER" init --base-config="$BASE_CONFIG" --override-config="$OVERRIDE_CONFIG"
else
  "$PROVISIONER" init --base-config="$BASE_CONFIG"
fi

if [ $# -eq 0 ]; then
  echo "jeffrey-jib: no command provided (JIB CMD missing); cannot launch JVM." >&2
  exit 1
fi

# JIB CMD is ["java","-cp","@/app/jib-classpath-file","<MainClass>"] or ["java","-jar",…].
# Insert @argfile right after the java binary — preserves the flag ordering of the pre-
# jeffrey-jib `java @/tmp/jvm.args -cp … MainClass` pattern, without the JDK_JAVA_OPTIONS
# "Picked up …" startup noise.
JAVA_BIN="$1"
shift
exec "$JAVA_BIN" "@${ARG_FILE}" "$@"
