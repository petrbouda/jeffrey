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
