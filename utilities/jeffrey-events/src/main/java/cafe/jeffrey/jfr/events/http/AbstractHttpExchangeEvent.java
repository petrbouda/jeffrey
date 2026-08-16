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

import cafe.jeffrey.jfr.events.trace.AbstractTracedEvent;
import cafe.jeffrey.jfr.events.trace.SpanKind;
import cafe.jeffrey.jfr.events.trace.SpanName;
import cafe.jeffrey.jfr.events.trace.SpanOutcome;
import cafe.jeffrey.jfr.events.trace.SpanStatus;
import jdk.jfr.*;

/**
 * The half of an HTTP exchange that is the same on both sides of the wire, and the span shape both
 * derive the same way: named by the method and the matched URI template, failed from the response
 * status.
 * <p>
 * The convention is declared twice on purpose, and {@code SpanMetadataRoundTripTest} plus Jeffrey's
 * {@code EventApiContract} keep the two in agreement: {@link #describeSpan()} applies it at commit
 * so the recording reads correctly in {@code jfr print} and JMC, while {@link SpanName} and
 * {@link SpanOutcome} carry it in the recording's metadata for any reader — Jeffrey included — to
 * apply itself, even to an event committed without {@code commitSpan()}.
 */
@Category({"Application", "HTTP"})
@StackTrace(false)
@SpanName("{method} {uri}")
@SpanOutcome(from = "statusCode", semantics = SpanOutcome.HTTP_CODE)
public abstract class AbstractHttpExchangeEvent extends AbstractTracedEvent {

    /**
     * The lowest status counted as a failure. 4xx is the client's fault rather than the server's,
     * but it is still an exchange that did not deliver what was asked for, and a trace that hides
     * that is a trace nobody can debug a 404 storm from.
     */
    private static final int FIRST_ERROR_STATUS = 400;

    private static final String URI_SEPARATOR = " ";

    @Label("Remote Address")
    public String remoteHost;

    @Label("Remote Port")
    public int remotePort;

    @Label("HTTP Uri")
    public String uri;

    @Label("HTTP Method")
    public String method;

    @Label("Media Type")
    public String mediaType;

    @Label("Response Status")
    public int statusCode;

    @Label("Query Parameters")
    public String queryParams;

    @Label("Path Parameters")
    public String pathParams;

    @Label("Request Body Length")
    @DataAmount
    public long requestLength;

    @Label("Response Body Length")
    @DataAmount
    public long responseLength;

    protected AbstractHttpExchangeEvent(SpanKind kind) {
        this.kind = kind.name();
    }

    @Override
    protected void describeSpan() {
        name = method + URI_SEPARATOR + uri;
        if (statusCode >= FIRST_ERROR_STATUS) {
            status = SpanStatus.ERROR.name();
        }
    }
}
