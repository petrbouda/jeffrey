

package io.jafar.parser.api.types;

import io.jafar.parser.api.JfrType;
import io.jafar.parser.api.types.JFRClass;
import io.jafar.parser.api.types.JFRSymbol;

@JfrType("jdk.types.ClassLoader")
public interface JFRClassLoader {
    JFRClass type();
    JFRSymbol name();
}
