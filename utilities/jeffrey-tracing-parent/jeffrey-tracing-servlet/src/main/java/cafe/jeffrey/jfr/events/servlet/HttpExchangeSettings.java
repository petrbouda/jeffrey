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

package cafe.jeffrey.jfr.events.servlet;

/**
 * What the exchange filter records beyond the request's shape.
 * <p>
 * Both capture flags default to <b>off</b>, and deliberately so: a recording is a file that gets
 * uploaded, shared and kept, and query strings routinely carry access tokens, e-mail addresses and
 * search terms. An instrumentation library that exfiltrated those by default would be a liability
 * dressed as a convenience. Turn them on knowingly, for an application whose parameters you know
 * are safe to keep.
 *
 * @param captureQueryParams record query-string parameters as a JSON object on the event
 * @param capturePathParams  record the route's template variables as a JSON object on the event
 */
public record HttpExchangeSettings(boolean captureQueryParams, boolean capturePathParams) {

    /**
     * Records neither — the safe default.
     */
    public static HttpExchangeSettings defaults() {
        return new HttpExchangeSettings(false, false);
    }
}
