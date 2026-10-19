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

package cafe.jeffrey.profile.common.exceptions;

/**
 * Whose throw it was, as far as a reader can act on it.
 * <p>
 * Libraries count as the application on purpose: a {@code SocketTimeoutException} raised inside
 * Netty or a logback failure is something the application's owner has to deal with, so only the
 * JVM's own resolution and linking machinery is set apart. See {@link JvmInternalThrows}.
 */
public enum ThrowOrigin {

    /** Thrown by the application or a library it uses — what the reader is looking for. */
    APPLICATION,

    /** Thrown by the JVM while loading, linking or reflecting — noise nobody can act on. */
    JVM_INTERNAL
}
