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

package cafe.jeffrey.ide.plugin.idea.agent;

import com.intellij.openapi.util.SystemInfo;

import java.util.List;
import java.util.Locale;

/**
 * How a command line is quoted for the shell it is typed into.
 *
 * <p>The launcher does not run a process; it types the command into the terminal tab the developer
 * configured, and on Windows that is PowerShell or {@code cmd.exe} as often as a POSIX shell. Each
 * spells a literal argument and an environment variable differently, so the whole command — the
 * endpoint options, the endpoint variable and the prompt — is quoted by one strategy picked for
 * that shell rather than by POSIX rules that a Windows shell would type in as garbage.
 *
 * <p>{@code cmd.exe} has no escape that holds inside a quoted word: a {@code "} toggles quoting, and
 * after it {@code &}, {@code |}, {@code <}, {@code >}, {@code ^} and {@code %} are live again. So
 * {@link #CMD} refuses any of those rather than quoting them — none belongs in a Microscope URL or a
 * profile id — and refuses a {@code "} in a variable's value, where {@code set "NAME=value"} has no
 * way to carry one. PowerShell 7.3+ passes arguments to a native {@code .exe} with its own escaping,
 * which the {@code \"} used here for an embedded double quote is written against the legacy rules
 * of; only the JSON Claude is handed carries one.
 */
public enum ShellQuoting {

    /** bash, zsh, sh, fish, Git Bash and WSL: single quotes, with a quote closed, escaped and reopened. */
    POSIX {
        @Override
        public String quote(String argument) {
            return SINGLE_QUOTE + argument.replace(SINGLE_QUOTE, POSIX_ESCAPED_SINGLE_QUOTE) + SINGLE_QUOTE;
        }

        @Override
        public String environment(String name, String value) {
            return name + "=" + quote(value) + " ";
        }
    },

    /**
     * Windows PowerShell and pwsh: a single-quoted literal — nothing expands in it, and every
     * character PowerShell reads as a single quote, the typographic ones included, is doubled — with
     * embedded double quotes escaped by the Windows argument rules so a native executable receives
     * them rather than having them stripped. An argument ending in a backslash is refused: whether
     * PowerShell wraps the argument in quotes of its own decides whether that backslash would need
     * doubling, and a wrong guess either changes the value or escapes the closing quote.
     */
    POWERSHELL {
        @Override
        public String quote(String argument) {
            if (argument.endsWith(BACKSLASH)) {
                throw new IllegalArgumentException(
                        "PowerShell cannot pass an argument ending in a backslash safely: " + argument);
            }
            return literal(escapeQuotesForWindowsArgv(argument));
        }

        @Override
        public String environment(String name, String value) {
            return "$env:" + name + "=" + literal(value) + "; ";
        }

        private String literal(String value) {
            StringBuilder quoted = new StringBuilder(SINGLE_QUOTE);
            for (char character : value.toCharArray()) {
                quoted.append(character);
                if (POWERSHELL_SINGLE_QUOTES.indexOf(character) >= 0) {
                    quoted.append(character);
                }
            }
            return quoted.append(SINGLE_QUOTE).toString();
        }
    },

    /**
     * cmd.exe: double quotes, with an embedded one and the backslashes before it escaped by the
     * Windows argument rules and a trailing run of backslashes doubled so it cannot escape the
     * closing quote; {@code set "NAME=value" &&} for a variable. A value it cannot hold inertly is
     * refused (see the class comment).
     */
    CMD {
        @Override
        public String quote(String argument) {
            refuse(argument, CMD_METACHARACTERS);
            String escaped = escapeQuotesForWindowsArgv(argument);
            return DOUBLE_QUOTE + escaped + trailingBackslashes(escaped) + DOUBLE_QUOTE;
        }

        @Override
        public String environment(String name, String value) {
            refuse(value, CMD_METACHARACTERS);
            refuse(value, DOUBLE_QUOTE);
            return "set " + DOUBLE_QUOTE + name + "=" + value + DOUBLE_QUOTE + " && ";
        }

        private void refuse(String value, String forbidden) {
            for (char character : value.toCharArray()) {
                if (forbidden.indexOf(character) >= 0) {
                    throw new IllegalArgumentException(
                            "cmd.exe cannot hold '" + character + "' safely in a command: " + value);
                }
            }
        }
    };

    private static final String SINGLE_QUOTE = "'";
    private static final String DOUBLE_QUOTE = "\"";
    private static final String BACKSLASH = "\\";
    private static final char BACKSLASH_CHAR = '\\';
    private static final char DOUBLE_QUOTE_CHAR = '"';
    private static final String POSIX_ESCAPED_SINGLE_QUOTE = "'\\''";

    /** The apostrophe and the four typographic quotes PowerShell's tokenizer treats as one. */
    private static final String POWERSHELL_SINGLE_QUOTES = "'\u2018\u2019\u201A\u201B";

    /** What cmd.exe interprets once a double quote has toggled quoting off. */
    private static final String CMD_METACHARACTERS = "&|<>^%";

    private static final List<String> POWERSHELL_MARKERS = List.of("powershell", "pwsh");
    private static final List<String> CMD_SUFFIXES = List.of("cmd.exe", "cmd");

    /**
     * Escapes each double quote the way a Windows program splits its command line
     * ({@code CommandLineToArgvW}, the MSVC runtime): {@code 2n} backslashes before a quote are read
     * as {@code n} and the quote as a delimiter, {@code 2n+1} as {@code n} and a literal quote. So the
     * run of backslashes in front of a quote is doubled and one more is added for the quote itself;
     * backslashes anywhere else are literal and left as they are.
     */
    private static String escapeQuotesForWindowsArgv(String argument) {
        StringBuilder escaped = new StringBuilder();
        int backslashes = 0;
        for (char character : argument.toCharArray()) {
            if (character == BACKSLASH_CHAR) {
                backslashes++;
            } else if (character == DOUBLE_QUOTE_CHAR) {
                escaped.append(BACKSLASH.repeat(backslashes * 2 + 1)).append(character);
                backslashes = 0;
            } else {
                escaped.append(BACKSLASH.repeat(backslashes)).append(character);
                backslashes = 0;
            }
        }
        return escaped.append(BACKSLASH.repeat(backslashes)).toString();
    }

    /** As many backslashes again as {@code escaped} ends with, so the quote that follows closes it. */
    private static String trailingBackslashes(String escaped) {
        int count = 0;
        for (int index = escaped.length() - 1; index >= 0 && escaped.charAt(index) == BACKSLASH_CHAR; index--) {
            count++;
        }
        return BACKSLASH.repeat(count);
    }

    /** The argument as one literal word of this shell. */
    public abstract String quote(String argument);

    /** What sets {@code name} to {@code value} for the command typed after it, trailing separator included. */
    public abstract String environment(String name, String value);

    /**
     * The quoting for the shell a terminal tab runs.
     *
     * <p>Off Windows every shell the IDE offers takes POSIX quoting. On Windows the shell path says
     * which: PowerShell (the IDE's default there, and so the answer for an unknown path),
     * {@code cmd.exe}, or a POSIX shell such as Git Bash or WSL.
     *
     * @param shellPath the terminal's configured shell command, or null when unknown
     * @param windows   whether the IDE runs on Windows
     */
    public static ShellQuoting forShell(String shellPath, boolean windows) {
        if (!windows) {
            return POSIX;
        }
        if (shellPath == null || shellPath.isBlank()) {
            return POWERSHELL;
        }
        String shell = shellPath.trim().toLowerCase(Locale.ROOT);
        if (POWERSHELL_MARKERS.stream().anyMatch(shell::contains)) {
            return POWERSHELL;
        }
        if (CMD_SUFFIXES.stream().anyMatch(shell::endsWith)) {
            return CMD;
        }
        return POSIX;
    }

    /** The quoting for a command with no terminal to read the shell from — one pasted by hand. */
    public static ShellQuoting forPlatform() {
        return forShell(null, SystemInfo.isWindows);
    }
}
