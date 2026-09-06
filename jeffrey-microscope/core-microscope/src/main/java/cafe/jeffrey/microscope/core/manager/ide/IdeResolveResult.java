/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
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
