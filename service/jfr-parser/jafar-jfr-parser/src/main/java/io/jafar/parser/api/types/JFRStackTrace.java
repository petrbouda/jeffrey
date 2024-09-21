

package io.jafar.parser.api.types;

import io.jafar.parser.api.JfrType;
import io.jafar.parser.api.types.JFRStackFrame;

@JfrType("jdk.types.StackTrace")
public interface JFRStackTrace {
    boolean truncated();
    JFRStackFrame[] frames();
}
