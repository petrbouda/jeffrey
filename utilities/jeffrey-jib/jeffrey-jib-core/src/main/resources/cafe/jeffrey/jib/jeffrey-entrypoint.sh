#!/bin/sh
#
# Jeffrey JIB entrypoint wrapper.
#
# Runs `jeffrey-cli init` to populate /tmp/jvm.args, then exec's the command JIB originally
# produced (java -cp <classpath> <MainClass>, or java -jar <app.jar>, etc.) with the argfile
# inserted right after the java binary.
#
# Runtime kill switch:
#   JEFFREY_ENABLED  set to false|0|no|off (case-insensitive) to bypass profiling entirely
#                    and exec the JIB command verbatim — no jeffrey-cli init, no argfile.
#
# All paths are overridable via environment variables:
#   JEFFREY_HOME          required (unless JEFFREY_CLI_PATH is set directly)
#   JEFFREY_CLI_PATH      default ${JEFFREY_HOME}/libs/current/jeffrey-cli-<arch>
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

# If the caller hasn't told us where the CLI lives, log a warning and continue without
# profiling — the application must keep booting even when Jeffrey is not configured.
if [ -z "${JEFFREY_CLI_PATH:-}" ] && [ -z "${JEFFREY_HOME:-}" ]; then
  echo "jeffrey-jib: neither JEFFREY_HOME nor JEFFREY_CLI_PATH is set — Jeffrey is disabled, starting application without profiling." >&2
  echo "jeffrey-jib: to enable, set JEFFREY_HOME to the shared volume root (CLI at \${JEFFREY_HOME}/libs/current/jeffrey-cli-<arch>)," >&2
  echo "jeffrey-jib: or JEFFREY_CLI_PATH directly to the CLI binary." >&2
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

JEFFREY_CLI="${JEFFREY_CLI_PATH:-${JEFFREY_HOME}/libs/current/jeffrey-cli-${ARCH}}"
if [ ! -x "$JEFFREY_CLI" ]; then
  echo "jeffrey-jib: CLI not found or not executable: $JEFFREY_CLI" >&2
  echo "jeffrey-jib: set JEFFREY_HOME to the shared volume, or JEFFREY_CLI_PATH directly." >&2
  exit 1
fi

BASE_CONFIG="${JEFFREY_BASE_CONFIG:-/jeffrey/jeffrey-base.conf}"
OVERRIDE_CONFIG="${JEFFREY_OVERRIDE_CONFIG:-/jeffrey/jeffrey-overrides.conf}"
ARG_FILE="${JEFFREY_ARG_FILE:-/tmp/jvm.args}"

if [ -f "$OVERRIDE_CONFIG" ]; then
  "$JEFFREY_CLI" init --base-config="$BASE_CONFIG" --override-config="$OVERRIDE_CONFIG"
else
  "$JEFFREY_CLI" init --base-config="$BASE_CONFIG"
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
