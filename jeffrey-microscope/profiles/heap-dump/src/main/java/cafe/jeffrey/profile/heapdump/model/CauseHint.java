/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cafe.jeffrey.profile.heapdump.model;

/**
 * Diagnostic annotation explaining why a class loader is being held alive.
 * Hints are derived heuristically by walking the GC root path and matching well-known
 * leak patterns (ThreadLocal, JDBC driver registration, JNI globals, etc.).
 *
 * @param kind        the kind of leak pattern matched
 * @param description short human-readable description (e.g. "Thread.threadLocals")
 * @param objectId    the object id of the matched step in the GC root path, or {@code -1}
 *                    if the hint applies to the path overall (e.g. JNI root type)
 */
public record CauseHint(
        HintKind kind,
        String description,
        long objectId
) {
}
