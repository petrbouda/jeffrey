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
 * Outcome of an IDE open attempt. A {@code false} success with a human-readable message represents
 * an expected, non-fatal condition (e.g. the IDE plugin is not running), not a server error. The
 * {@link IdeFailureReason} lets the frontend decide whether to offer re-selecting a target.
 */
public record IdeOpenResult(boolean success, String message, IdeFailureReason reason) {

    public static IdeOpenResult succeeded() {
        return new IdeOpenResult(true, null, IdeFailureReason.NONE);
    }

    public static IdeOpenResult failed(String message, IdeFailureReason reason) {
        return new IdeOpenResult(false, message, reason);
    }
}
