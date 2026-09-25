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

package cafe.jeffrey.jfr.events.trace;

import jdk.jfr.MetadataDefinition;
import jdk.jfr.Description;
import jdk.jfr.Label;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares how a reader derives this event type's span name from the event's own fields, so the
 * naming convention travels <em>inside every recording</em> the event is written to.
 * <p>
 * The template names the operation the way OpenTelemetry does: {@code "{method} {uri}"} for an
 * HTTP exchange, {@code "{service}/{method}"} for a gRPC call. Each {@code {token}} is the name of
 * a field declared by the event; everything between tokens is literal text. A reader that finds
 * this annotation in the recording's metadata can name the span without knowing the event type —
 * which is what lets Jeffrey list an event it has never seen under a real operation name, even
 * when the event was committed with plain {@link jdk.jfr.Event#commit()} and
 * {@link AbstractTracedEvent#describeSpan()} never ran.
 * <p>
 * {@link MetadataDefinition} is what persists the annotation into the recording, and
 * {@link Inherited} is what makes a template declared on an abstract base — an HTTP exchange's,
 * say — appear on every concrete event type extending it, the same way {@link jdk.jfr.Category}
 * propagates.
 * <p>
 * This annotation plays no part in span <em>discovery</em>, which stays structural: an event is a
 * span because it declares a {@code spanId} field, i.e. because it extends
 * {@link AbstractTracedEvent}. A {@link TraceScopeEvent} declares {@code scopedSpanId} instead and
 * carries no template, so a scope can never be mistaken for a span whatever it is annotated with.
 *
 * <h2>Wire format</h2>
 * The template syntax — {@code {field}} tokens over literal text — is read out of recordings that
 * outlive every version of this library. It is frozen: extensions must be additive, and a token is
 * always a plain field name matching {@code [A-Za-z0-9_]+}.
 */
@MetadataDefinition
@Label("Span")
@Description("Template a reader derives this event's span name from; tokens name the event's own fields")
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Inherited
public @interface Span {

    /**
     * The naming template, e.g. {@code "{method} {uri}"}.
     */
    String value();
}
