

package io.jafar.parser.api.types;

import io.jafar.parser.api.JfrIgnore;
import io.jafar.parser.api.JfrType;
import io.jafar.parser.api.types.JFRClass;
import io.jafar.parser.api.types.JFRSymbol;

@JfrType("jdk.types.Method")
public interface JFRMethod {
    JFRClass type();
    io.jafar.parser.api.types.JFRSymbol name();
    JFRSymbol descriptor();
    int modifiers();
    boolean hidden();

    @JfrIgnore
    default String string() {
        return String.format("%s.%s%s", type().tostring(), name().string(), descriptor().string());
    }
}
