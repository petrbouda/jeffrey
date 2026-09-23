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

package cafe.jeffrey.shared.common.exception;

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
