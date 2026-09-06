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

    private static final char ADDRESS_MARKER_ZERO = '0';
    private static final char ADDRESS_MARKER_X = 'x';

    /**
     * Characters the JVM is known to put between the stable name and the address. None of them is
     * a hex digit, which is what makes the scan in {@link #split(String)} unambiguous.
     */
    private static final String SEPARATORS = "./+";

    /**
     * Splits a raw class name as it arrives from the recording. Names without a hidden-class
     * suffix are returned unchanged with a {@code null} identity.
     * <p>
     * This runs once per frame of every stacktrace in a recording, so it walks the name backwards
     * from the end rather than matching a pattern against it. A name that is not a hidden class —
     * the overwhelming majority — is rejected by the last character alone, and no name is scanned
     * further back than its own trailing address.
     * <p>
     * The backwards scan finds the only split point there can be. An address is {@code 0x} and a
     * run of hex digits reaching the end of the name, so the run of hex digits at the end fixes
     * where the {@code 0x} must sit, and the separator in front of it follows. A second, earlier
     * split point would have to span the separator of this one, and a separator is never a hex
     * digit — so at most one split point exists, and it is this one.
     */
    public static HiddenClassName split(String rawClassName) {
        if (rawClassName == null || rawClassName.isEmpty()) {
            return new HiddenClassName(rawClassName, null);
        }

        int addressStart = addressStart(rawClassName);
        if (addressStart < 0) {
            return new HiddenClassName(rawClassName, null);
        }

        return new HiddenClassName(
                rawClassName.substring(0, addressStart - 1),
                rawClassName.substring(addressStart));
    }

    /**
     * The index of the {@code 0} of a trailing {@code 0x…} address preceded by a separator and by
     * at least one character of class name, or {@code -1} when the name does not end in one.
     */
    private static int addressStart(String rawClassName) {
        int hexStart = rawClassName.length();
        while (hexStart > 0 && isHexDigit(rawClassName.charAt(hexStart - 1))) {
            hexStart--;
        }

        // No hex digits at the end, so nothing that could be an address.
        if (hexStart == rawClassName.length()) {
            return -1;
        }

        // The separator, the '0' and the 'x' all have to fit in front of the digits, and the
        // class name itself needs at least one character in front of the separator.
        int addressStart = hexStart - 2;
        if (addressStart < 2) {
            return -1;
        }

        boolean isAddress = rawClassName.charAt(hexStart - 1) == ADDRESS_MARKER_X
                && rawClassName.charAt(addressStart) == ADDRESS_MARKER_ZERO
                && SEPARATORS.indexOf(rawClassName.charAt(addressStart - 1)) >= 0;

        return isAddress ? addressStart : -1;
    }

    private static boolean isHexDigit(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }

    public boolean isHidden() {
        return hiddenClassId != null;
    }
}
