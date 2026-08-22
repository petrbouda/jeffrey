/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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

package cafe.jeffrey.jfr.events.grpc;

import cafe.jeffrey.jfr.events.trace.AbstractTracedEvent;
import cafe.jeffrey.jfr.events.trace.SpanKind;
import cafe.jeffrey.jfr.events.trace.Span;
import cafe.jeffrey.jfr.events.trace.SpanStatus;
import jdk.jfr.*;

/**
 * The half of a gRPC call that is the same on both sides of the wire, and the span shape both derive
 * the same way: named by the fully qualified method, failed by anything but {@code OK}.
 * <p>
 * The naming convention is declared twice on purpose, like the HTTP exchange: {@link #describeSpan()}
 * applies it at commit for readers of the raw recording, while {@link Span} carries it in the
 * recording's metadata for any reader to apply itself. The verdict is only recorded, never
 * declared — {@code commitSpan()} is the required path for failure detection.
 */
@Category({"Application", "gRPC"})
@StackTrace(false)
@Span("{service}/{method}")
public abstract class AbstractGrpcExchangeEvent extends AbstractTracedEvent {

    /** gRPC's own name for "the call succeeded"; every other code is a failed call. */
    private static final String OK_STATUS_CODE = "OK";

    private static final String METHOD_SEPARATOR = "/";

    @Label("Service Name")
    public String service;

    @Label("Method Name")
    public String method;

    @Label("Remote Address")
    public String remoteHost;

    @Label("Remote Port")
    public int remotePort;

    @Label("Status Code")
    public String statusCode;

    @Label("Authority")
    public String authority;

    @Label("Request Size")
    @DataAmount
    public long requestSize;

    @Label("Response Size")
    @DataAmount
    public long responseSize;

    protected AbstractGrpcExchangeEvent(SpanKind kind) {
        this.kind = kind.name();
    }

    @Override
    protected void describeSpan() {
        name = service + METHOD_SEPARATOR + method;
        // A failure recorded with failed() is the writer's statement and must not be painted over
        // by a derived verdict — deriving only escalates, it never downgrades an ERROR.
        if (!SpanStatus.ERROR.name().equals(status)) {
            status = OK_STATUS_CODE.equals(statusCode) ? SpanStatus.OK.name() : SpanStatus.ERROR.name();
        }
    }
}
