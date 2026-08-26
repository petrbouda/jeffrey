/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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

package cafe.jeffrey.shared.common;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A class name split into the part that is stable across JVM runs and, for a hidden class
 * (JEP 371, {@code Lookup.defineHiddenClass}), the per-run identity the JVM appends to it.
 * <p>
 * A hidden class has no entry in any class loader's dictionary, so the JVM makes its name unique
 * by appending its own address: {@code FilterChainProxy$$Lambda.0x0000000011cb1be8}. The address
 * is redrawn on every run, which makes the full name useless as an identity when two recordings
 * are compared. Everything in front of the address is stable and is what the rest of Jeffrey uses
 * as the class name.
 * <p>
 * The separator in front of the address varies by JDK and by the form the name reaches us in
 * ({@code /} in the JVM's external form, {@code +} in the internal form, {@code .} in what JFR
 * delivers), so all three are accepted. The suffix is unambiguous: a Java identifier cannot begin
 * with a digit, and neither {@code /} nor {@code +} is legal in a binary class name.
 * <p>
 * Common shapes in a real recording: {@code Host$$Lambda.0x…} (lambda proxies),
 * {@code java.lang.invoke.LambdaForm$MH.0x…} (method-handle forms) and
 * {@code java.lang.String$$StringConcat.0x…} (indified string concatenation).
 */
public record HiddenClassName(String className, String hiddenClassId) {

    private static final Pattern HIDDEN_CLASS_SUFFIX = Pattern.compile("^(.+)[./+](0x[0-9a-fA-F]+)$");

    /**
     * Splits a raw class name as it arrives from the recording. Names without a hidden-class
     * suffix are returned unchanged with a {@code null} identity.
     */
    public static HiddenClassName split(String rawClassName) {
        if (rawClassName == null || rawClassName.isEmpty()) {
            return new HiddenClassName(rawClassName, null);
        }

        Matcher matcher = HIDDEN_CLASS_SUFFIX.matcher(rawClassName);
        if (!matcher.matches()) {
            return new HiddenClassName(rawClassName, null);
        }

        return new HiddenClassName(matcher.group(1), matcher.group(2));
    }

    public boolean isHidden() {
        return hiddenClassId != null;
    }
}
