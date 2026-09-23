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

package cafe.jeffrey.microscope.core.manager.ide;

/**
 * Request to open a source location in the developer's IDE.
 *
 * @param profileId the profile this jump belongs to; used by the {@code default} bridge to look up
 *                  the cached IDE-window target. Nullable (the {@code jfr-profiler-plugin} bridge
 *                  ignores it).
 * @param fqn       fully-qualified class name (e.g. {@code com.example.OrderService})
 * @param method    method reference as {@code ClassName.methodName} (e.g. {@code OrderService.processOrder})
 * @param line      source line number, or {@code -1} when unknown
 */
public record IdeOpenRequest(String profileId, String fqn, String method, int line) {

    public IdeOpenRequest {
        if (fqn == null || fqn.isBlank()) {
            throw new IllegalArgumentException("fqn must not be blank");
        }
        if (method == null || method.isBlank()) {
            throw new IllegalArgumentException("method must not be blank");
        }
    }
}
