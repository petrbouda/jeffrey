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

#
# Jeffrey
# Copyright (C) 2026 Petr Bouda
#
# Builds and runs the local-only jeffrey-hub-stub.
#
# The stubs/ tree is intentionally NOT part of the root reactor (not listed in
# the root pom.xml <modules>), so it is never built in CI or pushed to releases.
# Because of that it must be built standalone, which requires the hub-api
# proto module (and its dependencies) to be available in the local ~/.m2 first.
#
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$REPO_ROOT"

echo ">> Installing hub-api (+ deps) into ~/.m2"
mvn -q -pl shared/hub-api -am install -DskipTests

echo ">> Building jeffrey-hub-stub"
mvn -q -f stubs/pom.xml clean package

echo ">> Starting jeffrey-hub-stub (gRPC, plaintext, port 8989)"
java -jar stubs/jeffrey-hub-stub/target/jeffrey-hub-stub.jar
