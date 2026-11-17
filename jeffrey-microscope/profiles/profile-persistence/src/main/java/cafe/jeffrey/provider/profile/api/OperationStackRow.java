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


package cafe.jeffrey.provider.profile.api;

import cafe.jeffrey.jfrparser.api.type.JfrStackTrace;

/**
 * The events one operation's traces committed on one stack — what an operation's entry into the reader's
 * code is read from.
 *
 * @param stackTrace the stack, root first
 * @param operation  the operation the traces are requests of: their root span's name, kind and event type
 * @param count      how many events
 */
public record OperationStackRow(JfrStackTrace stackTrace, TraceOperationId operation, long count) {
}
