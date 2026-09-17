#!/usr/bin/env bash
#
# Stages async-profiler's libasyncProfiler.so for linux/amd64 and linux/arm64 into the
# jeffrey-jib-payload-profiler module, ready for `mvn -P payload package`.
#
# The version is the async-profiler.version property of utilities/jeffrey-jib/pom.xml. It is
# pinned on purpose: this library is loaded into every profiled JVM, so which version ships is a
# decision recorded in the pom, never whatever happened to be the latest release on the day of
# the build. Downloads use `curl -f`, so a missing release or a rate-limited endpoint fails the
# build instead of staging an empty archive.
#
# Usage: build/scripts/stage-async-profiler-payload.sh [path/to/utilities/jeffrey-jib]
set -euo pipefail

JIB_DIR="${1:-$(cd "$(dirname "$0")/../.." && pwd)/utilities/jeffrey-jib}"
PAYLOAD_DIR="${JIB_DIR}/jeffrey-jib-payload-profiler/src/main/payload"

AP_VERSION=$(mvn -q -N -f "${JIB_DIR}/pom.xml" -DforceStdout help:evaluate -Dexpression=async-profiler.version)
if [[ ! "${AP_VERSION}" =~ ^[0-9]+\.[0-9]+ ]]; then
  echo "ERROR: could not read async-profiler.version from ${JIB_DIR}/pom.xml (got '${AP_VERSION}')" >&2
  exit 1
fi
echo "Staging async-profiler ${AP_VERSION}"

WORK_DIR=$(mktemp -d)
trap 'rm -rf "${WORK_DIR}"' EXIT

# async-profiler names its x86_64 archives "x64"; the payload classifiers follow the OCI
# platform names JIB uses.
for AP_ARCH_PAIR in "x64:amd64" "arm64:arm64"; do
  AP_ARCH="${AP_ARCH_PAIR%%:*}"
  OUT_ARCH="${AP_ARCH_PAIR##*:}"
  ARCHIVE="async-profiler-${AP_VERSION}-linux-${AP_ARCH}"
  curl -fsSL --retry 3 \
    "https://github.com/async-profiler/async-profiler/releases/download/v${AP_VERSION}/${ARCHIVE}.tar.gz" \
    | tar -xz -C "${WORK_DIR}"
  install -Dm644 "${WORK_DIR}/${ARCHIVE}/lib/libasyncProfiler.so" \
    "${PAYLOAD_DIR}/linux-${OUT_ARCH}/jeffrey-payload/libasyncProfiler.so"
done

ls -la "${PAYLOAD_DIR}"/linux-*/jeffrey-payload/
