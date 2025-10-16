/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package pbouda.jeffrey.exception;

public class JeffreyException extends RuntimeException {

    private final ErrorType type;
    private final ErrorCode code;

    public JeffreyException(ErrorType type, ErrorCode code, String message) {
        this(type, code, message, null);
    }

    public JeffreyException(ErrorType type, ErrorCode code, String message, Throwable cause) {
        super(message, cause);
        this.type = type;
        this.code = code;
    }

    public boolean isClientError() {
        return type == ErrorType.CLIENT;
    }

    public boolean isInternalError() {
        return type == ErrorType.INTERNAL;
    }

    public ErrorType getType() {
        return type;
    }

    public ErrorCode getCode() {
        return code;
    }
}
