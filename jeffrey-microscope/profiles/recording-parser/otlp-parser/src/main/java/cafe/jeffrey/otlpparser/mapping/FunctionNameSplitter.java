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
