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

package pbouda.jeffrey.profile.heapdump.model;

import java.util.List;

/**
 * Represents a single stack frame from a thread's stack trace with associated local variable references.
 *
 * @param className  the fully qualified class name of the method
 * @param methodName the method name
 * @param sourceFile the source file name (may be null)
 * @param lineNumber the line number in the source file (-1 if unavailable)
 * @param locals     list of local variable object references on this frame
 */
public record ThreadStackFrame(
        String className,
        String methodName,
        String sourceFile,
        int lineNumber,
        List<StackFrameLocal> locals
) {
}
