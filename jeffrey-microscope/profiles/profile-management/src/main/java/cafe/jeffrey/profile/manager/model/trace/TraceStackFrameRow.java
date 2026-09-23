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
