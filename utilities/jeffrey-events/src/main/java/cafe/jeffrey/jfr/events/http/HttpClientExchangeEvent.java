/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package cafe.jeffrey.jfr.events.http;

import cafe.jeffrey.jfr.events.trace.SpanKind;
import jdk.jfr.Description;
import jdk.jfr.Label;
import jdk.jfr.Name;

/**
 * An outbound HTTP call — a <b>leaf span</b>, committed with
 * {@link cafe.jeffrey.jfr.events.trace.AbstractTracedEvent#commitSpan() commitSpan()} in the
 * emitter's own {@code finally} so it nests under the span in progress. A transport failure that
 * never produced a status code is recorded with
 * {@link cafe.jeffrey.jfr.events.trace.AbstractTracedEvent#failed(Throwable) failed(Throwable)}.
 * See the {@linkplain cafe.jeffrey.jfr.events.http package documentation} for the full emit
 * pattern.
 */
@Name(HttpClientExchangeEvent.NAME)
@Label("HTTP Client Exchange")
@Description("Information about a single HTTP Client Request/Response Exchange")
public class HttpClientExchangeEvent extends AbstractHttpExchangeEvent {

    public static final String NAME = "jeffrey.HttpClientExchange";

    public HttpClientExchangeEvent() {
        super(SpanKind.CLIENT);
    }
}
