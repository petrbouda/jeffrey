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

package cafe.jeffrey.shared.notification;

import cafe.jeffrey.jfr.events.notification.Severity;

/**
 * Every kind of notification Jeffrey raises about its own work, with everything about it that does
 * not change between two occurrences.
 *
 * <h2>Why the text lives here and not at the call site</h2>
 * A notification's four low-cardinality fields — {@code type}, {@code category}, {@code severity} and
 * {@code message} — are declared once, on the constant, so that every occurrence of a kind records
 * <em>byte-identical</em> strings. That is not tidiness; it is what makes them cheap to store:
 *
 * <ul>
 *   <li><b>In the recording.</b> JFR interns every distinct string value in the per-chunk constant
 *       pool and stores each event's field as an index into it. Ten thousand identical messages cost
 *       one pool entry and ten thousand varints. A message assembled per call — with an id or a
 *       count in it — is a new pool entry every time, and is the single easiest way to make a
 *       recording enormous.</li>
 *   <li><b>In the profile database.</b> The searchable index references every distinct value text
 *       once, keyed by its hash, so a constant message is one row there however often it is raised.
 *       It is also what makes {@code message} usable as a search key at all: a key with one distinct
 *       value per occurrence is marked search-only and can never be broken down.</li>
 * </ul>
 *
 * <p>So the rule is: <strong>the message says what kind of thing happened; the attributes say which
 * one.</strong> If a sentence would need an id, a count, a duration or a class name spliced into it,
 * that value is an attribute — see {@link Notifications}. And if two occurrences genuinely need to
 * say different things, they are two kinds, not one kind with two messages.
 *
 * <h2>Frozen names</h2>
 * The constant's name reaches the recording as {@code type} and is read back out of recordings that
 * outlive the code that wrote them, so treat the names as frozen: add freely, rename never. The
 * message may be reworded — it is prose, and nothing matches on it — but rewording splits the
 * constant pool across recordings made before and after, which is harmless and worth knowing.
 *
 * <h2>What earns a constant here</h2>
 * A span already says what ran and how long. A notification is worth raising only when it says
 * something the span's duration cannot — a degradation accepted, a failure swallowed, something
 * destroyed, or a count that explains the shape of what happened.
 *
 * <h2>Severity</h2>
 * {@code LOW} routine success · {@code MEDIUM} degraded but recovered, or one deliberate destructive
 * act · {@code HIGH} work was lost, state is inconsistent, or one action destroyed many things ·
 * {@code CRITICAL} corruption or a crash.
 */
public enum NotificationType {

    // ---------- Deletions: destructive, irreversible, and otherwise silent ----------

    RECORDING_DELETED(NotificationCategory.RECORDING, Severity.MEDIUM,
            "The recording and its files were removed from the store"),

    RECORDING_GROUP_DELETED(NotificationCategory.RECORDING, Severity.HIGH,
            "A group was removed, taking every recording inside it"),

    PROFILE_DELETED(NotificationCategory.PROFILE, Severity.MEDIUM,
            "The profile's database and its whole directory were removed"),

    // ---------- Pipelines: profile init, heap dump init ----------

    PIPELINE_COMPLETED(NotificationCategory.PROFILE, Severity.LOW,
            "Pipeline run completed"),

    PIPELINE_FAILED(NotificationCategory.PROFILE, Severity.HIGH,
            "Pipeline run failed"),

    PIPELINE_CANCELLED(NotificationCategory.PROFILE, Severity.LOW,
            "Pipeline run cancelled"),

    PIPELINE_STAGE_FAILED(NotificationCategory.PROFILE, Severity.HIGH,
            "A stage threw, which is what ended the run"),

    // ---------- Parsing: data the profile silently does not contain ----------

    RECORDING_CHUNK_TRUNCATED(NotificationCategory.RECORDING, Severity.HIGH,
            "Chunk claimed more bytes than the file holds and was cut short"),

    RECORDING_CHUNK_DROPPED(NotificationCategory.RECORDING, Severity.HIGH,
            "Chunk header could not be read; this chunk and the rest of the recording were dropped"),

    RECORDING_HAS_NO_CHUNKS(NotificationCategory.RECORDING, Severity.HIGH,
            "Recording held no chunks, so the profile has no events to show"),

    RECORDING_DECOMPRESSION_FALLBACK(NotificationCategory.RECORDING, Severity.MEDIUM,
            "Streaming decompression failed; the whole recording was decompressed to disk first"),

    RECORDING_CHUNK_FAILURE_SWALLOWED(NotificationCategory.RECORDING, Severity.MEDIUM,
            "A chunk parse failed while unwinding an already-failing disassembly"),

    // ---------- Heap dumps ----------

    HEAP_DUMP_TRUNCATED(NotificationCategory.HEAP_DUMP, Severity.HIGH,
            "Heap dump was indexed from an incomplete file; objects are missing"),

    /**
     * Kept apart from {@link #HEAP_DUMP_TRUNCATED} rather than sharing it with a different message:
     * warnings mean the parse skipped something it did not understand, truncation means the file
     * stopped early, and they are neither the same finding nor the same severity.
     */
    HEAP_DUMP_PARSE_WARNINGS(NotificationCategory.HEAP_DUMP, Severity.MEDIUM,
            "Heap dump was indexed with parse warnings"),

    // ---------- Work that failed after the response had already been sent ----------

    PROFILE_CREATION_FAILED(NotificationCategory.PROFILE, Severity.HIGH,
            "Building a profile threw on a background thread, after the request had returned"),

    PROFILE_ANALYSIS_FAILED(NotificationCategory.PROFILE, Severity.HIGH,
            "Analysing the recording failed; the half-built profile was removed again"),

    DOWNLOAD_FAILED(NotificationCategory.RECORDING, Severity.MEDIUM,
            "Downloading a recording session failed"),

    DOWNLOAD_ARTIFACT_MISSING(NotificationCategory.RECORDING, Severity.HIGH,
            "An artifact could not be downloaded; the rest of the download still succeeded"),

    // ---------- Hub operations that only half happened ----------

    WORKSPACE_DELETE_PARTIAL(NotificationCategory.WORKSPACE, Severity.HIGH,
            "Workspace was removed locally, but the hub still holds it"),

    PROFILE_DIR_ORPHANED(NotificationCategory.PROFILE, Severity.MEDIUM,
            "Profile directory outlived the profile and is now orphaned on disk"),

    PROFILE_VIEW_WARMUP_FAILED(NotificationCategory.PROFILE, Severity.LOW,
            "A cached view could not be warmed; it will be computed on demand");

    private final NotificationCategory category;
    private final Severity severity;
    private final String message;

    NotificationType(NotificationCategory category, Severity severity, String message) {
        this.category = category;
        this.severity = severity;
        this.message = message;
    }

    /** The bucket a reader filters by before looking at any one type. */
    public NotificationCategory category() {
        return category;
    }

    /** How serious this kind is — the same answer every time, so it can be filtered on. */
    public Severity severity() {
        return severity;
    }

    /**
     * The sentence a reader sees. Constant by construction: there is deliberately no way to vary it
     * per occurrence, because that is what keeps it to one constant-pool entry per chunk and one row
     * in the profile's value index.
     */
    public String message() {
        return message;
    }
}
