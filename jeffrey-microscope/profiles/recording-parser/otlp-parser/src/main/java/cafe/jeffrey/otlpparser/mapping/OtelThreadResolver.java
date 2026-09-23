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

package cafe.jeffrey.otlpparser.mapping;

import io.opentelemetry.proto.common.v1.AnyValue;
import cafe.jeffrey.provider.profile.api.EventThread;

import java.util.Map;

/**
 * Resolves the thread of an OTLP sample. OTLP has no thread table — thread identity travels as the
 * semconv sample attributes {@code thread.name} / {@code thread.id} when the producer emits them;
 * samples without any thread attribute fall back to a synthetic per-resource thread so that every
 * event still lands on a thread lane in the UI.
 */
public final class OtelThreadResolver {

    /**
     * Name format for threads that only carry an OS thread id — matches the JFR parser's convention
     * for unnamed threads so downstream name-resolution treats them uniformly.
     */
    private static final String TID_NAME_PREFIX = "[tid=";
    private static final String TID_NAME_SUFFIX = "]";

    private OtelThreadResolver() {
    }

    public static EventThread resolve(Map<String, AnyValue> sampleAttributes, String fallbackThreadName) {
        String threadName = OtlpAttributes.stringValue(sampleAttributes.get(OtelSemconv.THREAD_NAME));
        Long threadId = OtlpAttributes.longValue(sampleAttributes.get(OtelSemconv.THREAD_ID));

        if (threadName != null && !threadName.isBlank()) {
            return new EventThread(threadName, threadId, null, false);
        }
        if (threadId != null) {
            return new EventThread(TID_NAME_PREFIX + threadId + TID_NAME_SUFFIX, threadId, null, false);
        }
        return new EventThread(fallbackThreadName, null, null, false);
    }
}
