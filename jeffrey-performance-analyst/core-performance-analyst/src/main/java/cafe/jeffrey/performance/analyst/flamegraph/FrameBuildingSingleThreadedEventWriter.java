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

package cafe.jeffrey.performance.analyst.flamegraph;

import cafe.jeffrey.frameir.Frame;
import cafe.jeffrey.frameir.FrameBuilder;
import cafe.jeffrey.jfrparser.api.type.JfrStackFrameImpl;
import cafe.jeffrey.jfrparser.api.type.JfrStackTrace;
import cafe.jeffrey.jfrparser.api.type.JfrStackTraceImpl;
import cafe.jeffrey.provider.profile.api.Event;
import cafe.jeffrey.provider.profile.api.EventFrame;
import cafe.jeffrey.provider.profile.api.EventStacktrace;
import cafe.jeffrey.provider.profile.api.FlamegraphRecord;
import cafe.jeffrey.provider.profile.api.SingleThreadedEventWriter;
import cafe.jeffrey.shared.common.model.Type;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Thread-confined writer that folds events straight into in-memory {@link Frame} trees instead of
 * persisting them. {@code JfrEventReader} hands stacktraces/threads to this writer (receiving back the
 * ids it then stamps onto each {@link Event}); on each event we re-join the stacktrace by id, wrap its
 * {@link EventFrame}s as a {@link JfrStackTrace} and feed a per-event-type {@link FrameBuilder}.
 *
 * <p>Threads are unused for the flamegraph (thread-mode is off, so {@code FrameNameBuilder} never reads
 * the thread), so {@code onEventThread} is left as the interface default. Its {@code -1} return is still
 * load-bearing: {@code JfrEventReader} records a stacktrace only when the event has a non-null thread id.
 */
public class FrameBuildingSingleThreadedEventWriter implements SingleThreadedEventWriter {

    private static final long NO_WEIGHT = 0L;

    private final Map<Long, JfrStackTrace> stacktraces = new HashMap<>();
    private final Map<Type, FrameBuilder> builders = new HashMap<>();

    private long stacktraceIdSeq = 0;

    @Override
    public long onEventStacktrace(EventStacktrace stacktrace) {
        long id = ++stacktraceIdSeq;
        stacktraces.put(id, toJfrStackTrace(id, stacktrace));
        return id;
    }

    @Override
    public void onEvent(Event event) {
        Long stacktraceId = event.stacktraceId();
        if (stacktraceId == null) {
            return;
        }
        JfrStackTrace stackTrace = stacktraces.get(stacktraceId);
        if (stackTrace == null) {
            return;
        }

        Type type = Type.fromCode(event.eventType());
        long weight = event.weight() == null ? NO_WEIGHT : event.weight();
        FlamegraphRecord record = new FlamegraphRecord(type, stackTrace, null, null, event.samples(), weight);
        builders.computeIfAbsent(type, _ -> newFrameBuilder()).onRecord(record);
    }

    /**
     * Builds one {@link Frame} tree per event type seen by this writer.
     */
    public Map<Type, Frame> result() {
        Map<Type, Frame> result = new HashMap<>();
        for (Map.Entry<Type, FrameBuilder> entry : builders.entrySet()) {
            result.put(entry.getKey(), entry.getValue().build());
        }
        return result;
    }

    private static FrameBuilder newFrameBuilder() {
        // lambdaFrameHandling=false, threadModeEnabled=false, parseLocations=true, no top-frame processor.
        return new FrameBuilder(false, false, true, null);
    }

    private static JfrStackTrace toJfrStackTrace(long id, EventStacktrace stacktrace) {
        List<EventFrame> eventFrames = stacktrace.frames();
        List<JfrStackFrameImpl> frames = new ArrayList<>(eventFrames.size());
        for (EventFrame eventFrame : eventFrames) {
            frames.add(new JfrStackFrameImpl(
                    eventFrame.clazz(),
                    eventFrame.method(),
                    eventFrame.type(),
                    (int) eventFrame.line(),
                    (int) eventFrame.bci()));
        }
        return new JfrStackTraceImpl(id, frames);
    }
}
