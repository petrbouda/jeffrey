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

package cafe.jeffrey.provisioner;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits a JVM command line into individual options, and quotes an option so it survives a
 * round trip through a {@code java @argfile}.
 *
 * <p>The profiler command is assembled from free-form text — {@code additional-jvm-options} and
 * hub-pushed profiler settings — so an option may legitimately carry a space inside quotes
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
