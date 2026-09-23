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
package cafe.jeffrey.profile.heapdump.oql.parser;

/**
 * Surface for OQL parse, desugar, and type-resolution failures.
 *
 * <p>{@code line} and {@code charPosition} are 1-based and 0-based
 * respectively (matching ANTLR's convention). They may be {@code -1} when the
 * error has no specific position (e.g. desugaring failures that affect a
 * whole subtree).
 */
public class OqlParseException extends RuntimeException {

    private final int line;
    private final int charPosition;

    public OqlParseException(String message, int line, int charPosition) {
        super(message);
        this.line = line;
        this.charPosition = charPosition;
    }

    public OqlParseException(String message, int line, int charPosition, Throwable cause) {
        super(message, cause);
        this.line = line;
        this.charPosition = charPosition;
    }

    public OqlParseException(String message) {
        this(message, -1, -1);
    }

    public int line() {
        return line;
    }


    /** Formatted "{@code line:column}" location, or {@code "<unknown>"} when no position is set. */
    public String location() {
        if (line < 0) {
            return "<unknown>";
        }
        return line + ":" + charPosition;
    }
}
