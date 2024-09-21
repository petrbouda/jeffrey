

package io.jafar.parser.api.types;

import io.jafar.parser.api.JfrType;

@JfrType("jdk.types.FrameType")
public interface JFRFrameType {
    String description();
}
