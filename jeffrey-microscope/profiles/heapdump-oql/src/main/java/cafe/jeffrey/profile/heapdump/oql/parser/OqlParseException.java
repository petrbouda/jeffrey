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

    public int charPosition() {
        return charPosition;
    }

    /** Formatted "{@code line:column}" location, or {@code "<unknown>"} when no position is set. */
    public String location() {
        if (line < 0) {
            return "<unknown>";
        }
        return line + ":" + charPosition;
    }
}
