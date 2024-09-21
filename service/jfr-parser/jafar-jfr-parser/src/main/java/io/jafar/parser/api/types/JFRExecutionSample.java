

package io.jafar.parser.api.types;

import io.jafar.parser.api.JfrType;
import io.jafar.parser.api.types.JFREvent;
import io.jafar.parser.api.types.JFRStackTrace;
import io.jafar.parser.api.types.JFRThreadState;

@JfrType("jdk.ExecutionSample")
public interface JFRExecutionSample extends JFREvent {
    JFRStackTrace stackTrace();
    JFRThreadState state();
}
