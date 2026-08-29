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
