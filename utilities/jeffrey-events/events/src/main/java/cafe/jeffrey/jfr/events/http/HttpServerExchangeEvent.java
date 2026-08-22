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
