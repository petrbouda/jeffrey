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

package cafe.jeffrey.profile.heapdump.model;

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
