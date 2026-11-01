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

package cafe.jeffrey.microscope.core.mcp.tools.jvm;

/**
 * A process command line cut to a length an answer can carry.
 * <p>
 * A Java process's command line is mostly its class path, and the parts that say what the process is —
 * the executable at the start, the main class or jar and its arguments at the end — sit on either side
 * of it. So the cut keeps both ends and drops the middle, marked by {@link #ELLIPSIS}. One 37,893-character
 * line took {@code jvm_system} to 42,000 characters; twenty of them would pass the client's limit.
 */
public final class CommandLines {

    /** The most characters of a command line an answer carries, the marker included. */
    public static final int MAX_CHARS = 600;

    private static final String MARK = "…";

    static final String ELLIPSIS = " " + MARK + " ";

    private static final int HEAD_CHARS = (MAX_CHARS - ELLIPSIS.length()) / 2;
    private static final int TAIL_CHARS = MAX_CHARS - ELLIPSIS.length() - HEAD_CHARS;

    public static final String DESCRIPTION = "Cut to at most " + MAX_CHARS + " characters, keeping its start "
            + "and its end (the executable, and a Java process's main class and arguments) around \""
            + MARK + "\"; the full length is in the Chars field beside it";

    private CommandLines() {
    }

    /**
     * @param commandLine a command line as recorded, possibly null
     * @return it whole when it fits {@link #MAX_CHARS}, otherwise its start and end around the marker
     */
    public static String cut(String commandLine) {
        if (commandLine == null || commandLine.length() <= MAX_CHARS) {
            return commandLine;
        }
        return commandLine.substring(0, HEAD_CHARS) + ELLIPSIS
                + commandLine.substring(commandLine.length() - TAIL_CHARS);
    }

    /**
     * @param commandLine a command line as recorded, possibly null
     * @return its full length, or null when there is none
     */
    public static Integer chars(String commandLine) {
        return commandLine == null ? null : commandLine.length();
    }
}
