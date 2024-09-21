

package io.jafar.parser.api.types;

import io.jafar.parser.api.JfrIgnore;
import io.jafar.parser.api.JfrType;

@JfrType("jdk.types.StackFrame")
public interface JFRStackFrame {
    JFRFrameType type();
    int lineNumber();
    int bytecodeIndex();
    JFRMethod method();

    @JfrIgnore
    default String string() {
        return String.format("%s:%d", method().string(), lineNumber());
    }
}
