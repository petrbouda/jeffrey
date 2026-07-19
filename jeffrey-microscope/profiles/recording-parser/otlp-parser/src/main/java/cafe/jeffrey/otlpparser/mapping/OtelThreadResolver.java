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
