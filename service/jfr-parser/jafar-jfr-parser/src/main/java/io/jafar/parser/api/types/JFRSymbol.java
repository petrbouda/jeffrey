

package io.jafar.parser.api.types;

import io.jafar.parser.api.JfrType;

@JfrType("jdk.types.Symbol")
public interface JFRSymbol {
    String string();
}
