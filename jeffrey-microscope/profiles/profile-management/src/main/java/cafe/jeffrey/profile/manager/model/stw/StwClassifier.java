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

package cafe.jeffrey.profile.manager.model.stw;

import tools.jackson.databind.node.ObjectNode;
import cafe.jeffrey.provider.profile.api.GenericRecord;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.shared.common.model.Type;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Normalizes raw pause events ({@code jdk.GarbageCollection}, {@code jdk.ExecuteVMOperation},
 * {@code jdk.SafepointStateSynchronization}, {@code jdk.JavaMonitorEnter}, {@code jdk.ThreadPark},
 * {@code jdk.VirtualThreadPinned}) into a single {@link StwEvent} shape so the timeline and budget
 * builders share one classification path.
 */
public final class StwClassifier {

    private static final String CAUSE_FIELD = "cause";
    private static final String OPERATION_FIELD = "operation";
    private static final String SAFEPOINT_FIELD = "safepoint";
    private static final String MONITOR_CLASS_FIELD = "monitorClass";
    private static final String PARKED_CLASS_FIELD = "parkedClass";
    private static final String EVENT_THREAD_FIELD = "eventThread";
    private static final String GC_ID_FIELD = "gcId";
    private static final String SUM_OF_PAUSES_FIELD = "sumOfPauses";
    private static final String SAFEPOINT_SYNC_LABEL = "Safepoint sync";

    /**
     * Per-source extraction rules. {@code labelField == null} means use the category label verbatim.
     */
    private record Descriptor(
            StwCategory category,
            String labelField,
            String threadField,
            boolean readGcId,
            boolean preferSumOfPauses,
            boolean requireSafepoint) {
    }

    private static final Map<Type, Descriptor> DESCRIPTORS = Map.of(
            Type.GARBAGE_COLLECTION,
            new Descriptor(StwCategory.GC_PAUSE, CAUSE_FIELD, null, true, true, false),
            Type.EXECUTE_VM_OPERATION,
            new Descriptor(StwCategory.VM_OPERATION, OPERATION_FIELD, null, false, false, true),
            Type.SAFEPOINT_STATE_SYNCHRONIZATION,
            new Descriptor(StwCategory.TIME_TO_SAFEPOINT, null, null, false, false, false),
            Type.JAVA_MONITOR_ENTER,
            new Descriptor(StwCategory.MONITOR, MONITOR_CLASS_FIELD, EVENT_THREAD_FIELD, false, false, false),
            Type.THREAD_PARK,
            new Descriptor(StwCategory.PARK, PARKED_CLASS_FIELD, EVENT_THREAD_FIELD, false, false, false),
            Type.VIRTUAL_THREAD_PINNED,
            new Descriptor(StwCategory.PINNED, null, EVENT_THREAD_FIELD, false, false, false));

    /** The event types the timeline streams; pass to {@code EventQueryConfigurer.withEventTypes}. */
    public static final List<Type> SOURCE_TYPES = List.copyOf(DESCRIPTORS.keySet());

    private StwClassifier() {
    }

    /**
     * Maps a raw record to an {@link StwEvent}, or {@code null} when the record is not a pause source or
     * is a non-safepoint VM operation (which does not stop the world).
     */
    public static StwEvent classify(GenericRecord record) {
        Descriptor descriptor = DESCRIPTORS.get(record.type());
        if (descriptor == null) {
            return null;
        }

        ObjectNode fields = record.jsonFields();
        if (descriptor.requireSafepoint() && !Json.readBoolean(fields, SAFEPOINT_FIELD)) {
            return null;
        }

        long durationNanos = resolveDurationNanos(record, fields, descriptor);
        String label = resolveLabel(fields, descriptor);
        String thread = descriptor.threadField() == null ? null : Json.readString(fields, descriptor.threadField());
        Long gcId = resolveGcId(fields, descriptor);

        return new StwEvent(
                descriptor.category(),
                descriptor.category().scope(),
                record.timestampFromStart().toMillis(),
                durationNanos,
                label,
                thread,
                gcId);
    }

    private static long resolveDurationNanos(GenericRecord record, ObjectNode fields, Descriptor descriptor) {
        if (descriptor.preferSumOfPauses()) {
            long sumOfPauses = Json.readLong(fields, SUM_OF_PAUSES_FIELD);
            if (sumOfPauses > 0) {
                return sumOfPauses;
            }
        }
        Duration duration = record.duration();
        return duration == null ? 0 : duration.toNanos();
    }

    private static String resolveLabel(ObjectNode fields, Descriptor descriptor) {
        if (descriptor.labelField() == null) {
            return descriptor.category() == StwCategory.TIME_TO_SAFEPOINT
                    ? SAFEPOINT_SYNC_LABEL
                    : descriptor.category().label();
        }
        String value = Json.readString(fields, descriptor.labelField());
        return value == null ? descriptor.category().label() : value;
    }

    private static Long resolveGcId(ObjectNode fields, Descriptor descriptor) {
        if (!descriptor.readGcId()) {
            return null;
        }
        long gcId = Json.readLong(fields, GC_ID_FIELD);
        return gcId >= 0 ? gcId : null;
    }
}
