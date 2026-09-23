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

package cafe.jeffrey.provisioner;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits a JVM command line into individual options, and quotes an option so it survives a
 * round trip through a {@code java @argfile}.
 *
 * <p>The profiler command is assembled from free-form text — {@code additional-jvm-options} and
 * {@code profiler-command} — so an option may legitimately carry a space inside quotes
 * ({@code -Djeffrey.dir="/opt/my app"}). Splitting on whitespace alone tears such an option
 * across two argfile lines, and the JVM then refuses the file; the failure surfaces as
 * "application will not start" rather than as a provisioner error.
 */
public abstract class JvmOptions {

    private static final char DOUBLE_QUOTE = '"';
    private static final char SINGLE_QUOTE = '\'';
    private static final char BACKSLASH = '\\';

    /** No quote is currently open. */
    private static final char NOT_QUOTED = 0;

    /**
     * Splits {@code command} on whitespace that sits outside quotes. The quote characters
     * themselves are consumed, so {@code -Dp="/opt/my app"} yields the single option
     * {@code -Dp=/opt/my app}; {@link #quoteForArgFile(String)} puts the quoting back.
     */
    public static List<String> split(String command) {
        List<String> options = new ArrayList<>();
        if (command == null || command.isBlank()) {
            return options;
        }

        StringBuilder option = new StringBuilder();
        boolean started = false;
        char openQuote = NOT_QUOTED;

        for (int i = 0; i < command.length(); i++) {
            char current = command.charAt(i);
            if (openQuote != NOT_QUOTED) {
                if (current == openQuote) {
                    openQuote = NOT_QUOTED;
                } else {
                    option.append(current);
                }
                continue;
            }
            if (current == DOUBLE_QUOTE || current == SINGLE_QUOTE) {
                openQuote = current;
                // An empty quoted section is still an option: -Dp="" must not vanish.
                started = true;
                continue;
            }
            if (Character.isWhitespace(current)) {
                if (started) {
                    options.add(option.toString());
                    option.setLength(0);
                    started = false;
                }
                continue;
            }
            option.append(current);
            started = true;
        }

        if (started) {
            options.add(option.toString());
        }
        return options;
    }

    /**
     * Wraps {@code option} in double quotes when it carries whitespace, escaping the characters
     * the JVM's argfile parser treats specially. An option without whitespace is written as-is,
     * which keeps the generated file readable for the common case.
     */
    public static String quoteForArgFile(String option) {
        boolean needsQuotes = false;
        for (int i = 0; i < option.length(); i++) {
            if (Character.isWhitespace(option.charAt(i))) {
                needsQuotes = true;
                break;
            }
        }
        if (!needsQuotes) {
            return option;
        }

        StringBuilder quoted = new StringBuilder(option.length() + 2);
        quoted.append(DOUBLE_QUOTE);
        for (int i = 0; i < option.length(); i++) {
            char current = option.charAt(i);
            if (current == DOUBLE_QUOTE || current == BACKSLASH) {
                quoted.append(BACKSLASH);
            }
            quoted.append(current);
        }
        quoted.append(DOUBLE_QUOTE);
        return quoted.toString();
    }
}
