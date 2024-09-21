

package io.jafar.parser.api.types;

import io.jafar.parser.api.JfrType;

@JfrType("jdk.types.ThreadState")
public interface JFRThreadState {
    String name();
}
