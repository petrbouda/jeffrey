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
 * Request to fetch the source of a class from a locally-running IDE plugin.
 *
 * <p>The {@code method} carries no semantic meaning for source retrieval. It exists for the
 * {@code jfr-profiler-plugin} bridge alone, which rebuilds a {@code /ide/{fqn}.{method}} path and
 * drops the last dotted segment again at the other end. The first-party bridge asks the plugin for a
 * class and never reads it, which is why it is optional: insisting on it made every caller in that
 * mode invent one — the MCP tool passed the class name twice — to satisfy a requirement nothing
 * enforced downstream.
 *
 * @param profileId the profile this request belongs to; used by the {@code default} bridge to look
 *                  up the cached IDE-window target. Nullable (the {@code jfr-profiler-plugin} bridge
 *                  ignores it).
 * @param fqn       fully-qualified class name (e.g. {@code com.example.OrderService})
 * @param method    method reference as {@code ClassName.methodName} (e.g.
 *                  {@code OrderService.processOrder}), or null when the caller has no method in hand
 */
public record IdeSourceRequest(String profileId, String fqn, String method) {

    public IdeSourceRequest {
        if (fqn == null || fqn.isBlank()) {
            throw new IllegalArgumentException("fqn must not be blank");
        }
    }
}
