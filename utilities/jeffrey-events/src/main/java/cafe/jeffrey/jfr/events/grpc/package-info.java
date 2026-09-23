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

/**
 * gRPC exchange events: one event per call, on whichever side of the wire this process stands.
 * <p>
 * Both events share the {@link cafe.jeffrey.jfr.events.grpc.AbstractGrpcExchangeEvent} shape and
 * derive their span the same way — named {@code "{service}/{method}"}, failed by any status but
 * {@code OK}:
 * <ul>
 *   <li>{@link cafe.jeffrey.jfr.events.grpc.GrpcServerExchangeEvent} ({@code
 *       jeffrey.GrpcServerExchange}) — an inbound call, normally the <b>root of its trace</b></li>
 *   <li>{@link cafe.jeffrey.jfr.events.grpc.GrpcClientExchangeEvent} ({@code
 *       jeffrey.GrpcClientExchange}) — an outbound call, a <b>leaf</b> under the span in
 *       progress</li>
 * </ul>
 *
 * <h2>Emitting from interceptors</h2>
 * A gRPC call is not a single block of work: the handler runs from listener callbacks after the
 * interceptor has returned, on threads the interceptor does not control. The server exchange is
 * therefore opened <em>without</em> binding, via
 * {@link cafe.jeffrey.jfr.events.trace.Tracer#openSpanOf Tracer.openSpanOf}, and re-established
 * around every callback with {@link cafe.jeffrey.jfr.events.trace.Tracer#reenter Tracer.reenter} —
 * each re-entry also records which thread the call actually ran on:
 *
 * <pre>{@code
 * GrpcServerExchangeEvent event = new GrpcServerExchangeEvent();
 * if (!event.isEnabled()) {
 *     return next.startCall(call, headers);
 * }
 * event.begin();
 * SpanContext span = Tracer.openSpanOf(event);   // stamps the ids, binds nothing yet
 * event.service = call.getMethodDescriptor().getServiceName();
 * event.method = call.getMethodDescriptor().getBareMethodName();
 *
 * // ... in every wrapped listener callback (onMessage, onHalfClose, ...):
 * Tracer.reenter(span, () -> {
 *     super.onHalfClose();                       // the unary handler runs inside this one
 * });
 *
 * // ... in the wrapped ServerCall.close(status, trailers):
 * Tracer.reenter(span, () -> {
 *     event.statusCode = status.getCode().name();
 *     event.end();
 *     if (event.shouldCommit()) {
 *         event.commitSpan();
 *     }
 *     super.close(status, trailers);
 * });
 * }</pre>
 *
 * A blocking client call fits the simpler leaf shape instead — begin/end around the call,
 * {@link cafe.jeffrey.jfr.events.trace.AbstractTracedEvent#failed(Throwable) failed(Throwable)} on
 * the exception path, {@code commitSpan()} in the {@code finally} — exactly like an outbound HTTP
 * exchange (see {@link cafe.jeffrey.jfr.events.http}).
 */
package cafe.jeffrey.jfr.events.grpc;
