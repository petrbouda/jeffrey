/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

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
