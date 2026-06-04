#!/usr/bin/env bash
#
# Jeffrey
# Copyright (C) 2026 Petr Bouda
#
# This program is free software: you can redistribute it and/or modify
# it under the terms of the GNU Affero General Public License as published by
# the Free Software Foundation, either version 3 of the License, or
# (at your option) any later version.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU Affero General Public License for more details.
#
# You should have received a copy of the GNU Affero General Public License
# along with this program.  If not, see <http://www.gnu.org/licenses/>.
#
# Builds a native installer for the Jeffrey Microscope analyzer using the JDK's
# jpackage tool. jpackage is platform-bound: it can only emit the installer
# formats supported by the OS it runs on (dmg/pkg on macOS, msi on Windows,
# deb/rpm on Linux), so this script is invoked from per-OS CI runners.
#
# microscope.jar is a Spring Boot fat jar whose manifest Main-Class is the Spring
# Boot JarLauncher. We therefore pass only --main-jar (no --main-class), letting
# jpackage read the launcher from the manifest so nested BOOT-INF dependencies
# resolve correctly. The bundled runtime is taken from the JDK running this script.
#
# Usage: jpackage.sh <type> <input-dir> <dest-dir> <raw-version>
#   type        one of: dmg pkg msi deb rpm
#   input-dir   directory containing microscope.jar
#   dest-dir    directory the installer is written to
#   raw-version the release version, e.g. 0.10.0 or 0.10.0-b4

set -euo pipefail

if [ "$#" -ne 4 ]; then
    echo "usage: jpackage.sh <type> <input-dir> <dest-dir> <raw-version>" >&2
    exit 2
fi

TYPE="$1"
INPUT_DIR="$2"
DEST_DIR="$3"
RAW_VERSION="$4"

APP_NAME="microscope"
MAIN_JAR="microscope.jar"
VENDOR="Petr Bouda"
COPYRIGHT="Copyright (C) 2026 Petr Bouda"
DESCRIPTION="Jeffrey - the JFR Analyst"

# jpackage --app-version must be a plain numeric MAJOR[.MINOR[.PATCH]] with no
# pre-release suffix; macOS additionally rejects a MAJOR component of 0. Strip the
# -bN suffix, and on macOS promote a leading 0 major to 1 so dmg/pkg builds succeed.
# This only affects the version baked into the package metadata — the published
# asset filename keeps the true release version (the CI job renames the output).
BASE_VERSION="${RAW_VERSION%%-*}"
case "$(uname -s)" in
    Darwin)
        MAJOR="${BASE_VERSION%%.*}"
        if [ "$MAJOR" = "0" ]; then
            PKG_VERSION="1.${BASE_VERSION#*.}"
        else
            PKG_VERSION="$BASE_VERSION"
        fi
        ;;
    *)
        PKG_VERSION="$BASE_VERSION"
        ;;
esac

mkdir -p "$DEST_DIR"

EXTRA_ARGS=()
if [ "$TYPE" = "msi" ]; then
    # Console launcher so server logs are visible; Start-menu + per-user dir chooser.
    EXTRA_ARGS+=(--win-console --win-menu --win-dir-chooser --win-shortcut)
fi

echo "jpackage: type=${TYPE} app_version=${PKG_VERSION} raw_version=${RAW_VERSION}"

"${JAVA_HOME}/bin/jpackage" \
    --type "$TYPE" \
    --name "$APP_NAME" \
    --app-version "$PKG_VERSION" \
    --input "$INPUT_DIR" \
    --main-jar "$MAIN_JAR" \
    --dest "$DEST_DIR" \
    --vendor "$VENDOR" \
    --copyright "$COPYRIGHT" \
    --description "$DESCRIPTION" \
    "${EXTRA_ARGS[@]}"

echo "jpackage: produced ->"
ls -la "$DEST_DIR"
