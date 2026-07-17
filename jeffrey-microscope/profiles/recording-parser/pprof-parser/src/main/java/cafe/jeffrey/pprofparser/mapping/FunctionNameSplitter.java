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

package cafe.jeffrey.pprofparser.mapping;

/**
 * Splits a pprof function name into the class/module and method parts of Jeffrey's frame model.
 * <p>
 * When the producer marks the class/method boundary with {@code '#'} (as Jeffrey's own exporter
 * does, e.g. {@code com.example.Foo#bar}), split there — the class keeps its dotted package context.
 * Otherwise fall back to the last {@code '.'}, which handles language-native dotted names (Go's
 * {@code main.processOrder}, {@code runtime.mallocgc}, {@code github.com/x/y.(*T).M}).
 */
public final class FunctionNameSplitter {

    public record SplitName(String clazz, String method) {
    }

    private static final char METHOD_SEPARATOR = '#';
    private static final char SEPARATOR = '.';

    private FunctionNameSplitter() {
    }

    public static SplitName split(String functionName) {
        if (functionName == null || functionName.isBlank()) {
            return new SplitName("", "");
        }
        int hash = functionName.indexOf(METHOD_SEPARATOR);
        if (hash >= 0) {
            return new SplitName(functionName.substring(0, hash), functionName.substring(hash + 1));
        }
        int lastDot = functionName.lastIndexOf(SEPARATOR);
        if (lastDot < 0) {
            return new SplitName("", functionName);
        }
        return new SplitName(functionName.substring(0, lastDot), functionName.substring(lastDot + 1));
    }
}
