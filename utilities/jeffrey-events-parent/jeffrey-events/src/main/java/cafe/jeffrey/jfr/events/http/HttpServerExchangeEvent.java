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
 * An inbound HTTP request/response exchange — normally the <b>root span</b> of the request's
 * trace, opened with {@link cafe.jeffrey.jfr.events.trace.Tracer#inSpanOf Tracer.inSpanOf} from a
 * servlet filter registered first in the chain. See the {@linkplain cafe.jeffrey.jfr.events.http
 * package documentation} for the full emit pattern.
 */
@Name(HttpServerExchangeEvent.NAME)
@Label("HTTP Server Exchange")
@Description("Information about a single HTTP Server Request/Response Exchange")
public class HttpServerExchangeEvent extends AbstractHttpExchangeEvent {

    public static final String NAME = "jeffrey.HttpServerExchange";

    public HttpServerExchangeEvent() {
        super(SpanKind.SERVER);
    }
}
