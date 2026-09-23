/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
