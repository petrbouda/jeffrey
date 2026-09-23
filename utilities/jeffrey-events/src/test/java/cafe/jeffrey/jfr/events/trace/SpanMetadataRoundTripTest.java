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

import cafe.jeffrey.jfr.events.http.HttpServerExchangeEvent;
import cafe.jeffrey.jfr.events.jdbc.statement.JdbcQueryEvent;
import cafe.jeffrey.jfr.events.test.JfrRecordings;
import jdk.jfr.AnnotationElement;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.consumer.RecordedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the two JFR behaviours the whole declared-conventions design rests on: a custom
 * {@code @MetadataDefinition} annotation with {@code String} elements is persisted into the
 * recording and read back through the consumer API, and {@code @Inherited} propagates a
 * declaration on an abstract base onto the concrete event types extending it.
 * <p>
 * The assertions go through {@link RecordedEvent#getEventType()} and
 * {@link jdk.jfr.EventType#getAnnotationElements()} — the exact API Jeffrey's parser reads event
 * metadata with — rather than through Java reflection, which would only prove the annotations
 * exist in the class file.
 */
class SpanMetadataRoundTripTest {

    private static final String SPAN_ANNOTATION_TYPE = Span.class.getName();

    /**
     * What third-party instrumentation looks like: not a Jeffrey type, declares its naming
     * convention itself, and is committed with plain {@code commit()} — no {@code describeSpan()},
     * no name recorded. The name still derives from the template; the verdict does not exist,
     * because a verdict is the writer's statement and only {@code commitSpan()} records one.
     */
    @Name(ThirdPartyEvent.NAME)
    @Label("Third Party Event")
    @Span("PUBLISH {topic}")
    static class ThirdPartyEvent extends AbstractTracedEvent {

        static final String NAME = "com.acme.KafkaPublish";

        @Label("Topic")
        public String topic;

        @Label("Delivery Code")
        public int deliveryCode;
    }

    @Test
    @DisplayName("a third-party event's declared template survives the recording round trip")
    void thirdPartyDeclarationsRoundTrip() throws IOException {
        RecordedEvent event = JfrRecordings.single(ThirdPartyEvent.NAME, () -> {
            ThirdPartyEvent third = new ThirdPartyEvent();
            third.topic = "orders";
            third.deliveryCode = 503;
            third.commit();
        });

        assertEquals("PUBLISH {topic}", annotationValue(event, SPAN_ANNOTATION_TYPE, "value"));
    }

    @Test
    @DisplayName("a declaration on the abstract exchange base reaches the concrete event type")
    void inheritedDeclarationsRoundTrip() throws IOException {
        // @Inherited is load-bearing: the template lives on AbstractHttpExchangeEvent, and the
        // recording's metadata describes jeffrey.HttpServerExchange.
        RecordedEvent event = JfrRecordings.single(HttpServerExchangeEvent.NAME, () -> {
            HttpServerExchangeEvent exchange = new HttpServerExchangeEvent();
            exchange.method = "GET";
            exchange.uri = "/api/internal/health";
            exchange.statusCode = 200;
            exchange.commitSpan();
        });

        assertEquals("{method} {uri}", annotationValue(event, SPAN_ANNOTATION_TYPE, "value"));
    }

    @Test
    @DisplayName("a self-naming type declares the identity template")
    void selfNamingTypesDeclareTheIdentityTemplate() throws IOException {
        // The invariant: every span type this library ships carries its @Span. A statement's
        // template is the identity -- it names itself, at construction.
        RecordedEvent statement = JfrRecordings.single("jeffrey.JdbcQuery", () -> {
            JdbcQueryEvent query = new JdbcQueryEvent("listSpans", "PROFILE_EVENTS");
            query.commitSpan();
        });
        assertEquals("{name}", annotationValue(statement, SPAN_ANNOTATION_TYPE, "value"));

        RecordedEvent span = JfrRecordings.single(TraceSpanEvent.NAME, () -> {
            Tracer.run("hand.written", () -> {
            });
        });
        assertEquals("{name}", annotationValue(span, SPAN_ANNOTATION_TYPE, "value"));
    }

    @Test
    @DisplayName("a scope event declares no convention and no spanId, so it can never be a span")
    void scopeEventCarriesNoConventions() throws IOException {
        RecordedEvent event = JfrRecordings.single(TraceScopeEvent.NAME, () -> {
            TraceScopeEvent scope = new TraceScopeEvent();
            scope.traceId = 1;
            scope.scopedSpanId = 2;
            scope.commit();
        });

        assertTrue(findAnnotation(event, SPAN_ANNOTATION_TYPE).isEmpty());
        assertTrue(event.getEventType().getFields().stream().noneMatch(f -> f.getName().equals("spanId")),
                "discovery is structural on spanId, which a scope must not declare");
    }

    private static String annotationValue(RecordedEvent event, String annotationType, String element) {
        AnnotationElement annotation = findAnnotation(event, annotationType)
                .orElseThrow(() -> new AssertionError(
                        annotationType + " must be persisted in the recording's metadata"));
        Object value = annotation.getValue(element);
        assertTrue(value instanceof String, "a metadata element reads back as the String it was written as");
        return (String) value;
    }

    private static Optional<AnnotationElement> findAnnotation(RecordedEvent event, String annotationType) {
        return event.getEventType().getAnnotationElements().stream()
                .filter(annotation -> annotation.getTypeName().equals(annotationType))
                .findFirst();
    }

}
