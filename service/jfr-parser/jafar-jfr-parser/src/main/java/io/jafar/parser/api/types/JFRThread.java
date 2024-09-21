

package io.jafar.parser.api.types;

import io.jafar.parser.api.JfrType;

@JfrType("java.lang.Thread")
public interface JFRThread {
    long osThreadId();
    long javaThreadId();
    String osName();
    String javaName();
    boolean virtual();
}
