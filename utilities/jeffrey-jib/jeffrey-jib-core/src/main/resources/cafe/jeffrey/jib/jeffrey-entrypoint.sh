#!/bin/sh
#
# Jeffrey JIB entrypoint wrapper.
#
# Runs `provisioner init` to populate /tmp/jvm.args, then exec's the command JIB originally
# produced (java -cp <classpath> <MainClass>, or java -jar <app.jar>, etc.) with the argfile
# inserted right after the java binary.
#
# The provisioner and async-profiler are baked into this image by the jeffrey-jib build
# extension, under /opt/jeffrey. Nothing is fetched or waited for at container start.
#
# Runtime kill switch:
#   JEFFREY_ENABLED  set to false|0|no|off (case-insensitive) to bypass profiling entirely
#                    and exec the JIB command verbatim — no provisioner init, no argfile.
#
# Environment:
#   JEFFREY_PROVISIONER_PATH  the provisioner baked by the extension, or one an operator points
#                             at a binary already present in the base image
#   JEFFREY_PROVISIONER_KIND  native | jar — which build the path above refers to. Baked by the
#                             extension alongside the path; setting it by hand without a matching
#                             binary only fails open.
#   JEFFREY_PROFILER_PATH     libasyncProfiler.so, baked the same way
#   JEFFREY_BASE_CONFIG       default /jeffrey/jeffrey-base.conf (optional — without the file,
#                             the provisioner configures itself from JEFFREY_* env vars such as
#                             JEFFREY_PROJECT_NAME and JEFFREY_WORKSPACE_REF_ID)
#   JEFFREY_OVERRIDE_CONFIG   default /jeffrey/jeffrey-overrides.conf (optional)
#   JEFFREY_ARG_FILE          default /tmp/jvm.args
#
# Fail-open guarantee: any misconfiguration (missing binaries, broken config, failed init)
# starts the application WITHOUT profiling instead of preventing it from starting.

set -e

# Runtime kill switch — bypass everything and exec the JIB command as-is. This runs before the
# java binary is shifted off, so "$@" here is still the whole command.
case "${JEFFREY_ENABLED:-true}" in
  false|0|no|off|FALSE|NO|OFF|False|No|Off)
    if [ $# -eq 0 ]; then
      echo "jeffrey-jib: JEFFREY_ENABLED=${JEFFREY_ENABLED} but no command provided" >&2
      exit 1
    fi
    exec "$@"
    ;;
esac

if [ $# -eq 0 ]; then
  echo "jeffrey-jib: no command provided (JIB CMD missing); cannot launch JVM." >&2
  exit 1
fi

# JIB's CMD is ["java","-cp","@/app/jib-classpath-file","<MainClass>"] or ["java","-jar",…].
# Capture the java binary and shift it off once, here: every exec below is
# `exec "$JAVA_BIN" …` and every fail-open path must re-attach it.
JAVA_BIN="$1"
shift

case "$(uname -m)" in
  x86_64)  ARCH="amd64" ;;
  aarch64) ARCH="arm64" ;;
  *)       ARCH="$(uname -m)" ;;
esac

# Multi-platform images bake a single {arch} path per payload, because JIB layers are not
# per-platform and every architecture's file ships in every manifest of the index. Single-platform
# images bake a literal path and this loop is a no-op for them.
for var in JEFFREY_PROVISIONER_PATH JEFFREY_PROFILER_PATH; do
  eval "value=\${$var-}"
  case "$value" in
    *"{arch}"*)
      expanded=$(printf '%s' "$value" | sed "s/{arch}/${ARCH}/g")
      eval "export $var=\"\$expanded\""
      ;;
  esac
done

PROVISIONER="${JEFFREY_PROVISIONER_PATH:-}"
PROVISIONER_KIND="${JEFFREY_PROVISIONER_KIND:-native}"

if [ -z "$PROVISIONER" ]; then
  echo "jeffrey-jib: profiling DISABLED: JEFFREY_PROVISIONER_PATH is not set, so this image carries no provisioner." >&2
  echo "jeffrey-jib: build the image with the jeffrey-jib extension (it bakes one under /opt/jeffrey), or set JEFFREY_PROVISIONER_PATH." >&2
  exec "$JAVA_BIN" "$@"
fi

case "$PROVISIONER_KIND" in
  native)
    if [ ! -x "$PROVISIONER" ]; then
      echo "jeffrey-jib: profiling DISABLED: provisioner binary missing or not executable: $PROVISIONER" >&2
      exec "$JAVA_BIN" "$@"
    fi
    ;;
  jar)
    if [ ! -r "$PROVISIONER" ]; then
      echo "jeffrey-jib: profiling DISABLED: provisioner jar missing or unreadable: $PROVISIONER" >&2
      exec "$JAVA_BIN" "$@"
    fi
    ;;
  *)
    echo "jeffrey-jib: profiling DISABLED: unknown JEFFREY_PROVISIONER_KIND '${PROVISIONER_KIND}' (expected native or jar)." >&2
    exec "$JAVA_BIN" "$@"
    ;;
esac

BASE_CONFIG="${JEFFREY_BASE_CONFIG:-/jeffrey/jeffrey-base.conf}"
OVERRIDE_CONFIG="${JEFFREY_OVERRIDE_CONFIG:-/jeffrey/jeffrey-overrides.conf}"
ARG_FILE="${JEFFREY_ARG_FILE:-/tmp/jvm.args}"

# Make sure the argfile check below reflects THIS run, not a leftover file.
rm -f "$ARG_FILE"

# The one place the two provisioner builds differ. Inside a function "$@" is the FUNCTION's
# arguments, so the CMD tail in the caller's "$@" is untouched; JAVA_BIN, PROVISIONER and
# PROVISIONER_KIND are globals and read through fine.
#
# The jar build runs on the application's own JVM: it is the binary JIB put in CMD, so it is
# guaranteed present and needs no JAVA_HOME discovery. The flags keep that second JVM cheap —
# it does nothing but write an argfile.
run_provisioner() {
  case "$PROVISIONER_KIND" in
    native)
      "$PROVISIONER" "$@"
      ;;
    jar)
      "$JAVA_BIN" -XX:TieredStopAtLevel=1 -XX:+UseSerialGC -Xshare:auto -Xmx64m -jar "$PROVISIONER" "$@"
      ;;
  esac
}

# The config file is optional: when absent, the provisioner configures itself from
# JEFFREY_* environment variables (JEFFREY_PROJECT_NAME, JEFFREY_WORKSPACE_REF_ID, ...).
INIT_OK=true
if [ -f "$BASE_CONFIG" ]; then
  if [ -f "$OVERRIDE_CONFIG" ]; then
    run_provisioner init --base-config="$BASE_CONFIG" --override-config="$OVERRIDE_CONFIG" || INIT_OK=false
  else
    run_provisioner init --base-config="$BASE_CONFIG" || INIT_OK=false
  fi
else
  run_provisioner init || INIT_OK=false
fi

# Fail-open: a failed init (or an init that produced no argfile) starts the
# application without profiling instead of pointing the JVM at a missing argfile.
if [ "$INIT_OK" != "true" ] || [ ! -f "$ARG_FILE" ]; then
  echo "jeffrey-jib: profiling DISABLED: provisioner init failed or produced no argfile (${ARG_FILE}); starting application without profiling." >&2
  if [ "$PROVISIONER_KIND" = "jar" ]; then
    # By far the likeliest cause, and the JVM's own UnsupportedClassVersionError above is easy to
    # miss in a noisy startup log.
    echo "jeffrey-jib: if the error above is UnsupportedClassVersionError, this application's JVM is older than the provisioner jar. Rebuild the image with provisionerSource=native." >&2
  fi
  exec "$JAVA_BIN" "$@"
fi

# Insert @argfile right after the java binary — preserves the flag ordering of the pre-
# jeffrey-jib `java @/tmp/jvm.args -cp … MainClass` pattern, without the JDK_JAVA_OPTIONS
# "Picked up …" startup noise.
exec "$JAVA_BIN" "@${ARG_FILE}" "$@"
