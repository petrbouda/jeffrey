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

import java.time.Instant;

/**
 * Request to locate a source position in the developer's IDE without opening it.
 *
 * <p>The same question {@link IdeOpenRequest} asks, minus the jump. It exists for the callers that
 * only want to know where a frame lives — an MCP client grounding a finding in real files, or a
 * check made before offering a jump — because moving somebody's editor is a side effect they did not
 * ask for and cannot undo.
 *
 * @param profileId     the profile this lookup belongs to; selects the cached IDE window. Nullable
 * @param fqn           fully-qualified class name (e.g. {@code com.example.OrderService})
 * @param method        the method name; may be null when only the class is being located
 * @param line          source line number from the profile, or {@code -1} when unknown
 * @param recordingTime when the profile was recorded, used by the IDE to report whether the file has
 *                      been edited since. Null when unknown, and then nothing is reported as stale
 */
public record IdeResolveRequest(
        String profileId, String fqn, String method, int line, Instant recordingTime) {

    public IdeResolveRequest {
        if (fqn == null || fqn.isBlank()) {
            throw new IllegalArgumentException("fqn must not be blank");
        }
    }
}
