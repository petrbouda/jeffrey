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

package cafe.jeffrey.jfr.events.trace;

/**
 * How a span finished. {@link #UNSET} is the default: an operation that completed without the
 * instrumentation expressing an opinion is not the same as one explicitly declared successful.
 */
public enum SpanStatus {

    /** The instrumentation did not record an outcome. */
    UNSET,

    /** The operation completed as intended. */
    OK,

    /** The operation failed. The failure is described by the span's error type. */
    ERROR
}
