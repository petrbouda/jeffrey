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

package cafe.jeffrey.profile.manager.model.trace;

/**
 * One frame of a throw's stack, as the UI reads it.
 *
 * @param className  the declaring class, or {@code null} for a native frame the recording could not
 *                   attribute to one — the UI shows the method alone rather than hiding the frame
 * @param methodName the method
 * @param frameType  JIT, Interpreted, Native or C++, kept because a frame the JIT inlined reads
 *                   differently from one that was interpreted
 * @param lineNumber the source line, or {@code null} when the recording captured none
 */
public record TraceStackFrameRow(
        String className,
        String methodName,
        String frameType,
        Integer lineNumber) {
}
