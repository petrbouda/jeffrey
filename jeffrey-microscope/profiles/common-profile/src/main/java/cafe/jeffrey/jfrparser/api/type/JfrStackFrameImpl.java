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

package cafe.jeffrey.jfrparser.api.type;

/**
 * Plain in-memory {@link JfrStackFrame}. The {@code type} is the raw JFR frame type string
 * (e.g. "Interpreted", "JIT compiled", "Inlined") that {@code FrameType.fromCode} understands.
 */
public record JfrStackFrameImpl(
        JfrMethodImpl method, String type, int lineNumber, int bytecodeIndex) implements JfrStackFrame {

    public JfrStackFrameImpl(String className, String methodName, String type, int lineNumber, int bytecodeIndex) {
        this(className, methodName, null, type, lineNumber, bytecodeIndex);
    }

    public JfrStackFrameImpl(
            String className,
            String methodName,
            String hiddenClassId,
            String type,
            int lineNumber,
            int bytecodeIndex) {

        this(new JfrMethodImpl(className, methodName, hiddenClassId), type, lineNumber, bytecodeIndex);
    }
}
