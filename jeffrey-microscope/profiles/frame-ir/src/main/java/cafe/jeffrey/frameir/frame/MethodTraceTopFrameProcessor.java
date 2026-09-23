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

package cafe.jeffrey.frameir.frame;

import cafe.jeffrey.profile.common.model.FrameType;
import cafe.jeffrey.jfrparser.api.type.JfrMethod;
import cafe.jeffrey.jfrparser.api.type.JfrMethodImpl;
import cafe.jeffrey.jfrparser.api.type.JfrStackFrame;
import cafe.jeffrey.provider.profile.api.FlamegraphRecord;

import java.util.List;

/**
 * Puts the traced method back on its own stack.
 *
 * <p>JEP 520 roots a {@code jdk.MethodTrace} stack trace at the <b>caller</b>: the event names the
 * method it traced in its {@code method} field, and the stack starts one frame above. Built from
 * the stack alone, the graph therefore ends at whoever called the traced method and the traced
 * method itself -- the only method the user asked to measure -- never appears in it, while its
 * caller sits at the leaf holding time it did not spend itself.
 *
 * <p>The synthesized frame is named exactly as {@link FrameNameBuilder} names a real Java frame
 * ({@code Class#method}), which is what makes it more than a label: a traced method that also shows
 * up as a caller of another traced method resolves to the same name at the same position, so the
 * two merge into one node instead of standing beside each other as a synthetic and a real frame
 * that happen to be the same method.
 */
public class MethodTraceTopFrameProcessor extends SingleFrameProcessor {

    private static final String NAME_DELIMITER = "#";

    @Override
    public NewFrame processSingle(FlamegraphRecord record, JfrStackFrame currFrame) {
        return new NewFrame(
                frameName(record),
                currFrame.lineNumber(),
                currFrame.bytecodeIndex(),
                FrameType.TRACED_METHOD_SYNTHETIC,
                record.samples(),
                record.weight());
    }

    @Override
    int consumedStackFrames() {
        // Emits the traced method below the real leaf (its caller) without consuming any stacktrace element.
        return 0;
    }

    @Override
    public boolean isApplicable(FlamegraphRecord record, List<? extends JfrStackFrame> stacktrace, int currIndex) {
        // Only the method-shaped entity carries a method name to append. An entity that parses to a
        // bare class means the recording did not say what was traced -- an older profile parsed
        // before the entity named the traced method, or an event whose method field was absent --
        // and a frame named after the class alone would sit in the graph looking like a method that
        // never ran. Emitting nothing leaves the graph exactly as it was before this processor.
        return currIndex == (stacktrace.size() - 1) && tracedMethod(record) != null;
    }

    private static String frameName(FlamegraphRecord record) {
        JfrMethod method = tracedMethod(record);
        return method.className() + NAME_DELIMITER + method.methodName();
    }

    private static JfrMethod tracedMethod(FlamegraphRecord record) {
        if (record.weightEntity() == null) {
            return null;
        }

        JfrMethod method = JfrMethodImpl.of(record.weightEntity().className());
        return method != null && method.methodName() != null ? method : null;
    }
}
