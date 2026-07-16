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

package cafe.jeffrey.otlpparser.mapping;

/**
 * Splits a flat JVM function name from an OTLP {@code Function} (e.g.
 * {@code com.example.Foo$Bar.doWork(Ljava/lang/String;)V}) into the class and method parts of
 * Jeffrey's frame model. Any method signature (from the first {@code '('}) is stripped first, then
 * the name is split at the last {@code '.'}.
 */
public final class FunctionNameSplitter {

    public record SplitName(String clazz, String method) {
    }

    private static final char SIGNATURE_START = '(';
    private static final char PACKAGE_SEPARATOR = '.';

    private FunctionNameSplitter() {
    }

    public static SplitName split(String functionName) {
        if (functionName == null || functionName.isBlank()) {
            return new SplitName("", "");
        }

        String withoutSignature = functionName;
        int signatureIndex = functionName.indexOf(SIGNATURE_START);
        if (signatureIndex >= 0) {
            withoutSignature = functionName.substring(0, signatureIndex);
        }

        int lastDot = withoutSignature.lastIndexOf(PACKAGE_SEPARATOR);
        if (lastDot < 0) {
            return new SplitName("", withoutSignature);
        }
        return new SplitName(
                withoutSignature.substring(0, lastDot),
                withoutSignature.substring(lastDot + 1));
    }
}
