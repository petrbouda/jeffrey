

package io.jafar.parser.api.types;

import io.jafar.parser.api.types.JFRStackTrace;
import io.jafar.parser.api.types.JFRThread;

public interface JFREvent {
    long startTime();
    JFRThread eventThread();
    JFRStackTrace stackTrace();
}
