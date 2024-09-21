

package io.jafar.parser.api.types;

import io.jafar.parser.api.JfrType;
import io.jafar.parser.api.types.JFRClassLoader;
import io.jafar.parser.api.types.JFRSymbol;

@JfrType("jdk.types.Module")
public interface JFRModule {
    io.jafar.parser.api.types.JFRSymbol name();
    io.jafar.parser.api.types.JFRSymbol version();
    JFRSymbol location();
    JFRClassLoader classLoader();
}
