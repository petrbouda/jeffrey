

package io.jafar.parser.api.types;

import io.jafar.parser.api.JfrField;
import io.jafar.parser.api.JfrIgnore;
import io.jafar.parser.api.JfrType;
import io.jafar.parser.api.types.JFRPackage;
import io.jafar.parser.api.types.JFRSymbol;

@JfrType("java.lang.Class")
public interface JFRClass {
    JFRSymbol name();
    @JfrField("package")
    io.jafar.parser.api.types.JFRPackage pkg();
    int modifiers();
    boolean hidden();

    @JfrIgnore
    default String tostring() {
        StringBuilder sb = new StringBuilder();
        JFRPackage pkg = pkg();
        if (pkg != null) {
            sb.append(pkg.string()).append(".");
        }
        sb.append(name().string());
        return sb.toString();
    }
}
