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
 * Outcome of an IDE source-fetch attempt. A {@code false} success with a human-readable message
 * represents an expected, non-fatal condition (e.g. the IDE plugin is offline or has no source for
 * the class), not a server error. On success {@code content} holds the raw source text.
 */
public record IdeSourceResult(boolean success, String content, String message, boolean decompiled) {

    public static IdeSourceResult succeeded(String content, boolean decompiled) {
        return new IdeSourceResult(true, content, null, decompiled);
    }

    public static IdeSourceResult failed(String message) {
        return new IdeSourceResult(false, null, message, false);
    }
}
