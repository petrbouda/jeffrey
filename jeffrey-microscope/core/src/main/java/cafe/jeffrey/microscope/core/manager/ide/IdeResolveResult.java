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
 * Where the IDE says a frame lives.
 *
 * <p>The three flags are the difference between a location a reader can cite and one they cannot,
 * which is the whole reason this carries more than a path and a line:
 *
 * <ul>
 *   <li>{@code decompiled} — the file is inside a jar with no sources attached, so the text is a
 *       decompiler's reconstruction and its line numbers are not the ones anybody wrote.</li>
 *   <li>{@code imprecise} — the IDE landed on the class or method declaration rather than the
 *       requested line, so the position names the right member and not the right statement.</li>
 *   <li>{@code stale} — the file has been modified well after the recording was taken, so the line
 *       may describe code that no longer exists.</li>
 * </ul>
 *
 * @param success     whether a location was found at all
 * @param file        absolute path of the resolved file; null when not found
 * @param line        1-based line in that file; null when not found
 * @param kind        how the IDE resolved it ({@code JAVA_PRECISE}, {@code JAVA_LINE},
 *                    {@code KOTLIN_LINE}, {@code KOTLIN_FALLBACK}); null when not found
 * @param sourceMTime when the file was last modified, as reported by the IDE; null when not found
 * @param message     why nothing was found, in a sentence; null on success
 */
public record IdeResolveResult(
        boolean success,
        String file,
        Integer line,
        String kind,
        boolean decompiled,
        boolean imprecise,
        boolean stale,
        String sourceMTime,
        String message) {

    public static IdeResolveResult failed(String message) {
        return new IdeResolveResult(false, null, null, null, false, false, false, null, message);
    }
}
