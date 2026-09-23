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

import cafe.jeffrey.jfr.events.trace.AbstractTracedEvent;
import cafe.jeffrey.jfr.events.trace.SpanKind;
import cafe.jeffrey.jfr.events.trace.Span;
import cafe.jeffrey.jfr.events.trace.SpanStatus;
import jdk.jfr.*;

/**
 * The half of an HTTP exchange that is the same on both sides of the wire, and the span shape both
 * derive the same way: named by the method and the matched URI template, failed from the response
 * status.
 * <p>
 * The naming convention is declared twice on purpose, and {@code SpanMetadataRoundTripTest} plus
 * Jeffrey's {@code EventApiContract} keep the two in agreement: {@link #describeSpan()} applies it
 * at commit so the recording reads correctly in {@code jfr print} and JMC, while {@link Span}
 * carries it in the recording's metadata for any reader — Jeffrey included — to apply itself, even
 * to an event committed without {@code commitSpan()}. The <em>verdict</em> is different: it is the
 * writer's statement, not a derivable mapping — an exchange that threw and still answered 200
 * knows something its code does not — so it is only ever recorded, by {@link #describeSpan()},
 * which is why {@code commitSpan()} is the required path for failure detection.
 */
@Category({"Application", "HTTP"})
@StackTrace(false)
@Span("{method} {uri}")
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
