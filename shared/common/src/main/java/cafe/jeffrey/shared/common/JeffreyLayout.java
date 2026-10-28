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

package cafe.jeffrey.shared.common;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;

/**
 * The on-disk layout contract of the shared workspaces filesystem.
 *
 * <p>The provisioner (writer) creates this structure on the shared volume, and every reader takes
 * it from the directory names alone: a level is the listing of its parent, and an id is a
 * directory name. Nothing below a workspace is described by a marker file:</p>
 *
 * <pre>
 * &lt;workspaces&gt;/
 *   &lt;workspace-ref-id&gt;/          its .workspace-info.json names it when it has another name
 *     &lt;project-name&gt;/
 *       &lt;instance-name&gt;/
 *         &lt;session-uuidv7&gt;/        the session's start is in its name
 *           profile-&lt;local time&gt;.&lt;zone&gt;.jfr
 * </pre>
 *
 * <p>async-profiler writes {@code %t} in the machine's local time, so the provisioner writes the
 * machine's zone into each recording's name ({@link #recordingTemplate}), and a reader turns the
 * name into an instant from the name alone ({@link #zoneOfSegment}).</p>
 *
 * <p>Both sides must resolve these names from this single class; any rename
 * silently desynchronizes producer and consumer otherwise.</p>
 */
public abstract class JeffreyLayout {

    /** Directory under the Jeffrey home that holds all workspaces */
    public static final String WORKSPACES_DIR = "workspaces";

    /** async-profiler's placeholder for the time a recording's file was opened. */
    public static final String TIMESTAMP_PLACEHOLDER = "%t";

    /** Separates a recording name's timestamp from the zone it is local to. */
    public static final String ZONE_SEGMENT_SEPARATOR = ".";

    private static final String RECORDING_EXTENSION = ".jfr";

    /** What a zone id's {@code /} becomes in a file name, which cannot hold one. */
    private static final char ZONE_PATH_SEPARATOR = '/';
    private static final char ZONE_NAME_SEPARATOR = '~';
    private static final String OFFSET_SEPARATOR = ":";
    private static final String UTC_SEGMENT = "UTC";

    /**
     * A recording's file template with the machine's zone in it: {@code prefix-%t.<zone>.jfr} for
     * {@code prefix-%t.jfr} — {@code profile-20261027-085500.Europe~Prague.jfr} once async-profiler
     * fills in {@code %t}.
     *
     * @param template a template ending in {@code %t.jfr}
     */
    public static String recordingTemplate(String template, ZoneId zone) {
        String suffix = TIMESTAMP_PLACEHOLDER + RECORDING_EXTENSION;
        if (!template.endsWith(suffix)) {
            throw new IllegalArgumentException("A recording template must end in " + suffix + ": " + template);
        }
        String head = template.substring(0, template.length() - RECORDING_EXTENSION.length());
        return head + ZONE_SEGMENT_SEPARATOR + zoneSegment(zone) + RECORDING_EXTENSION;
    }

    /**
     * A zone as a file-name segment: {@code Europe~Prague}, {@code UTC}, {@code +0530} — no
     * character a file share refuses in a name.
     */
    public static String zoneSegment(ZoneId zone) {
        if (zone.normalized().equals(ZoneOffset.UTC)) {
            return UTC_SEGMENT;
        }
        return zone.getId()
                .replace(ZONE_PATH_SEPARATOR, ZONE_NAME_SEPARATOR)
                .replace(OFFSET_SEPARATOR, "");
    }

    /** The zone a file-name segment names, or empty for one that names no zone this JVM knows. */
    public static Optional<ZoneId> zoneOfSegment(String segment) {
        try {
            return Optional.of(ZoneId.of(segment.replace(ZONE_NAME_SEPARATOR, ZONE_PATH_SEPARATOR)));
        } catch (DateTimeException e) {
            return Optional.empty();
        }
    }
}
